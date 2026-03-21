package ice

import (
	stdctx "context"
	"sync"

	"github.com/zjn-zjn/ice/sdks/go/cache"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/handler"
	icelog "github.com/zjn-zjn/ice/sdks/go/log"
)

func syncDispatcher(ctx stdctx.Context, roam *Roam) []*Roam {
	if !checkRoam(ctx, roam) {
		return nil
	}

	meta := roam.GetMeta()
	if meta.Trace != "" {
		ctx = icelog.WithTraceId(ctx, meta.Trace)
	}

	// First: iceId
	if meta.Id > 0 {
		h := cache.GetHandlerById(meta.Id)
		if h == nil {
			icelog.Debug(ctx, "handler maybe expired", "iceId", meta.Id)
			return nil
		}
		h.Handle(ctx, roam)
		return []*Roam{roam}
	}

	// Second: scene
	if meta.Scene != "" {
		handlerMap := cache.GetHandlersByScene(meta.Scene)
		if len(handlerMap) == 0 {
			icelog.Debug(ctx, "handlers maybe all expired", "scene", meta.Scene)
			return nil
		}

		if len(handlerMap) == 1 {
			for _, h := range handlerMap {
				meta.Id = h.IceId
				h.Handle(ctx, roam)
				return []*Roam{roam}
			}
		}

		// Multiple handlers - execute in parallel
		roamList := make([]*Roam, 0, len(handlerMap))
		var wg sync.WaitGroup

		for _, h := range handlerMap {
			clonedRoam := roam.Clone()
			clonedRoam.GetMeta().Id = h.IceId
			roamList = append(roamList, clonedRoam)

			wg.Add(1)
			hCopy := h
			clonedCopy := clonedRoam
			go func() {
				defer wg.Done()
				hCopy.Handle(ctx, clonedCopy)
			}()
		}
		wg.Wait()
		return roamList
	}

	// Third: confId
	confId := meta.Nid
	if confId <= 0 {
		return nil
	}

	root := cache.GetConfById(confId)
	if root != nil {
		h := &handler.Handler{
			Debug:  meta.Debug,
			Root:   root,
			ConfId: confId,
		}
		h.Handle(ctx, roam)
		return []*Roam{roam}
	}

	return nil
}

func asyncDispatcher(ctx stdctx.Context, roam *Roam) []<-chan *Roam {
	if !checkRoam(ctx, roam) {
		return nil
	}

	meta := roam.GetMeta()
	if meta.Trace != "" {
		ctx = icelog.WithTraceId(ctx, meta.Trace)
	}

	// First: iceId
	if meta.Id > 0 {
		h := cache.GetHandlerById(meta.Id)
		if h == nil {
			return nil
		}
		ch := submitHandler(ctx, h, roam)
		return []<-chan *Roam{ch}
	}

	// Second: scene
	if meta.Scene != "" {
		handlerMap := cache.GetHandlersByScene(meta.Scene)
		if len(handlerMap) == 0 {
			return nil
		}

		if len(handlerMap) == 1 {
			for _, h := range handlerMap {
				meta.Id = h.IceId
				ch := submitHandler(ctx, h, roam)
				return []<-chan *Roam{ch}
			}
		}

		chs := make([]<-chan *Roam, 0, len(handlerMap))
		for _, h := range handlerMap {
			clonedRoam := roam.Clone()
			clonedRoam.GetMeta().Id = h.IceId
			ch := submitHandler(ctx, h, clonedRoam)
			chs = append(chs, ch)
		}
		return chs
	}

	// Third: confId
	confId := meta.Nid
	if confId <= 0 {
		return nil
	}

	root := cache.GetConfById(confId)
	if root != nil {
		h := &handler.Handler{
			Debug:  meta.Debug,
			Root:   root,
			ConfId: confId,
		}
		ch := submitHandler(ctx, h, roam)
		return []<-chan *Roam{ch}
	}

	return nil
}

func submitHandler(ctx stdctx.Context, h *handler.Handler, roam *Roam) <-chan *Roam {
	ch := make(chan *Roam, 1)

	go func() {
		defer close(ch)
		h.Handle(ctx, roam)
		ch <- roam
	}()

	return ch
}

func checkRoam(ctx stdctx.Context, roam *Roam) bool {
	if roam == nil {
		icelog.Error(ctx, "invalid roam null")
		return false
	}
	meta := roam.GetMeta()
	if meta == nil {
		icelog.Error(ctx, "invalid roam no meta")
		return false
	}
	if meta.Id > 0 {
		return true
	}
	if meta.Scene != "" {
		return true
	}
	if meta.Nid > 0 {
		return true
	}
	icelog.Error(ctx, "invalid roam none iceId none scene none confId", "roam", roam)
	return false
}

// newRoamForDispatch creates a Roam with meta initialized from the handler for dispatch.
func newRoamForDispatch(h *handler.Handler, sourceRoam *icecontext.Roam) *icecontext.Roam {
	cloned := sourceRoam.Clone()
	cloned.GetMeta().Id = h.IceId
	return cloned
}

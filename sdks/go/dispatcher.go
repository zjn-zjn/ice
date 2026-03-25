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

	trace := roam.GetTrace()
	if trace != "" {
		ctx = icelog.WithTraceId(ctx, trace)
	}

	// First: iceId
	if roam.GetId() > 0 {
		h := cache.GetHandlerById(roam.GetId())
		if h == nil {
			icelog.Debug(ctx, "handler not found", "iceId", roam.GetId())
			return nil
		}
		h.Handle(ctx, roam)
		return []*Roam{roam}
	}

	// Second: scene
	scene := roam.GetScene()
	if scene != "" {
		handlerMap := cache.GetHandlersByScene(scene)
		if len(handlerMap) == 0 {
			icelog.Debug(ctx, "no handlers found", "scene", scene)
			return nil
		}

		if len(handlerMap) == 1 {
			for _, h := range handlerMap {
				roam.SetId(h.IceId)
				h.Handle(ctx, roam)
				return []*Roam{roam}
			}
		}

		// Multiple handlers - execute in parallel
		roamList := make([]*Roam, 0, len(handlerMap))
		var wg sync.WaitGroup

		for _, h := range handlerMap {
			clonedRoam := roam.Clone()
			clonedRoam.SetId(h.IceId)
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
	confId := roam.GetNid()
	if confId <= 0 {
		return nil
	}

	root := cache.GetConfById(confId)
	if root != nil {
		h := &handler.Handler{
			Debug:  roam.GetDebug(),
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

	trace := roam.GetTrace()
	if trace != "" {
		ctx = icelog.WithTraceId(ctx, trace)
	}

	// First: iceId
	if roam.GetId() > 0 {
		h := cache.GetHandlerById(roam.GetId())
		if h == nil {
			return nil
		}
		ch := submitHandler(ctx, h, roam)
		return []<-chan *Roam{ch}
	}

	// Second: scene
	scene := roam.GetScene()
	if scene != "" {
		handlerMap := cache.GetHandlersByScene(scene)
		if len(handlerMap) == 0 {
			return nil
		}

		if len(handlerMap) == 1 {
			for _, h := range handlerMap {
				roam.SetId(h.IceId)
				ch := submitHandler(ctx, h, roam)
				return []<-chan *Roam{ch}
			}
		}

		chs := make([]<-chan *Roam, 0, len(handlerMap))
		for _, h := range handlerMap {
			clonedRoam := roam.Clone()
			clonedRoam.SetId(h.IceId)
			ch := submitHandler(ctx, h, clonedRoam)
			chs = append(chs, ch)
		}
		return chs
	}

	// Third: confId
	confId := roam.GetNid()
	if confId <= 0 {
		return nil
	}

	root := cache.GetConfById(confId)
	if root != nil {
		h := &handler.Handler{
			Debug:  roam.GetDebug(),
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
		icelog.Error(ctx, "invalid roam: nil")
		return false
	}
	if roam.GetMeta() == nil {
		icelog.Error(ctx, "invalid roam: missing meta")
		return false
	}
	if roam.GetId() > 0 {
		return true
	}
	if roam.GetScene() != "" {
		return true
	}
	if roam.GetNid() > 0 {
		return true
	}
	icelog.Error(ctx, "invalid roam: no routing key", "roam", roam)
	return false
}

// newRoamForDispatch creates a Roam with meta initialized from the handler for dispatch.
func newRoamForDispatch(h *handler.Handler, sourceRoam *icecontext.Roam) *icecontext.Roam {
	cloned := sourceRoam.Clone()
	cloned.SetId(h.IceId)
	return cloned
}

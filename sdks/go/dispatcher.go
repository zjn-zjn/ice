package ice

import (
	stdctx "context"
	"sync"

	"github.com/zjn-zjn/ice/sdks/go/cache"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/handler"
	icelog "github.com/zjn-zjn/ice/sdks/go/log"
)

func syncDispatcher(ctx stdctx.Context, pack *Pack) []*Context {
	if !checkPack(ctx, pack) {
		return nil
	}

	// First: iceId
	if pack.IceId > 0 {
		h := cache.GetHandlerById(pack.IceId)
		if h == nil {
			icelog.Debug(ctx, "handler maybe expired", "iceId", pack.IceId)
			return nil
		}
		iceCtx := icecontext.NewContext(h.IceId, pack)
		h.Handle(ctx, iceCtx)
		return []*Context{iceCtx}
	}

	// Second: scene
	if pack.Scene != "" {
		handlerMap := cache.GetHandlersByScene(pack.Scene)
		if len(handlerMap) == 0 {
			icelog.Debug(ctx, "handlers maybe all expired", "scene", pack.Scene)
			return nil
		}

		if len(handlerMap) == 1 {
			for _, h := range handlerMap {
				iceCtx := icecontext.NewContext(h.IceId, pack)
				h.Handle(ctx, iceCtx)
				return []*Context{iceCtx}
			}
		}

		// Multiple handlers - execute in parallel
		roam := pack.Roam
		ctxList := make([]*Context, 0, len(handlerMap))
		var wg sync.WaitGroup

		for _, h := range handlerMap {
			newPack := pack.Clone()
			if roam != nil {
				newPack.Roam = roam.ShallowCopy()
			}
			iceCtx := icecontext.NewContext(h.IceId, newPack)
			ctxList = append(ctxList, iceCtx)

			wg.Add(1)
			hCopy := h
			go func() {
				defer wg.Done()
				hCopy.Handle(ctx, iceCtx)
			}()
		}
		wg.Wait()
		return ctxList
	}

	// Third: confId
	confId := pack.ConfId
	if confId <= 0 {
		return nil
	}

	root := cache.GetConfById(confId)
	if root != nil {
		iceCtx := icecontext.NewContext(confId, pack)
		h := &handler.Handler{
			Debug:  pack.Debug,
			Root:   root,
			ConfId: confId,
		}
		h.Handle(ctx, iceCtx)
		return []*Context{iceCtx}
	}

	return nil
}

func asyncDispatcher(ctx stdctx.Context, pack *Pack) []<-chan *Context {
	if !checkPack(ctx, pack) {
		return nil
	}

	// First: iceId
	if pack.IceId > 0 {
		h := cache.GetHandlerById(pack.IceId)
		if h == nil {
			return nil
		}
		iceCtx := icecontext.NewContext(h.IceId, pack)
		ch := submitHandler(ctx, h, iceCtx)
		return []<-chan *Context{ch}
	}

	// Second: scene
	if pack.Scene != "" {
		handlerMap := cache.GetHandlersByScene(pack.Scene)
		if len(handlerMap) == 0 {
			return nil
		}

		if len(handlerMap) == 1 {
			for _, h := range handlerMap {
				iceCtx := icecontext.NewContext(h.IceId, pack)
				ch := submitHandler(ctx, h, iceCtx)
				return []<-chan *Context{ch}
			}
		}

		roam := pack.Roam
		chs := make([]<-chan *Context, 0, len(handlerMap))
		for _, h := range handlerMap {
			newPack := pack.Clone()
			if roam != nil {
				newPack.Roam = roam.ShallowCopy()
			}
			iceCtx := icecontext.NewContext(h.IceId, newPack)
			ch := submitHandler(ctx, h, iceCtx)
			chs = append(chs, ch)
		}
		return chs
	}

	// Third: confId
	confId := pack.ConfId
	if confId <= 0 {
		return nil
	}

	root := cache.GetConfById(confId)
	if root != nil {
		iceCtx := icecontext.NewContext(confId, pack)
		h := &handler.Handler{
			Debug:  pack.Debug,
			Root:   root,
			ConfId: confId,
		}
		ch := submitHandler(ctx, h, iceCtx)
		return []<-chan *Context{ch}
	}

	return nil
}

func submitHandler(ctx stdctx.Context, h *handler.Handler, iceCtx *Context) <-chan *Context {
	ch := make(chan *Context, 1)

	go func() {
		defer close(ch)
		h.Handle(ctx, iceCtx)
		ch <- iceCtx
	}()

	return ch
}

func checkPack(ctx stdctx.Context, pack *Pack) bool {
	if pack == nil {
		icelog.Error(ctx, "invalid pack null")
		return false
	}
	if pack.IceId > 0 {
		return true
	}
	if pack.Scene != "" {
		return true
	}
	if pack.ConfId > 0 {
		return true
	}
	icelog.Error(ctx, "invalid pack none iceId none scene none confId", "pack", pack)
	return false
}

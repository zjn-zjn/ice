// Package handler provides the ice handler implementation.
package handler

import (
	stdctx "context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/internal/timeutil"
	"github.com/waitmoon/ice/sdks/go/log"
	"github.com/waitmoon/ice/sdks/go/node"
)

// DebugFlag constants for controlling debug output.
const (
	DebugInPack  byte = 1 << 0 // Log input pack
	DebugProcess byte = 1 << 1 // Log execution process
	DebugOutRoam byte = 1 << 2 // Log output roam
	DebugOutPack byte = 1 << 3 // Log output pack
)

// Handler represents an ice handler that processes requests.
type Handler struct {
	IceId    int64
	ConfId   int64
	Scenes   map[string]struct{}
	TimeType enum.TimeType
	Start    int64
	End      int64
	Debug    byte
	Root     node.Node
}

// NewHandler creates a new Handler.
func NewHandler() *Handler {
	return &Handler{
		Scenes: make(map[string]struct{}),
	}
}

// Handle processes a context.
func (h *Handler) Handle(ctx stdctx.Context, iceCtx *icecontext.Context) {
	if h.hasDebug(DebugInPack) {
		log.Info(ctx, "handle in", "iceId", h.IceId, "pack", iceCtx.Pack)
	}

	if timeutil.TimeDisabled(h.TimeType, iceCtx.Pack.RequestTime, h.Start, h.End) {
		return
	}

	if h.Root != nil {
		h.Root.Process(ctx, iceCtx)

		if h.hasDebug(DebugProcess) {
			log.Info(ctx, "handle process", "iceId", h.IceId, "process", iceCtx.ProcessInfo.String())
		}
		if h.hasDebug(DebugOutPack) {
			log.Info(ctx, "handle out", "iceId", h.IceId, "pack", iceCtx.Pack)
		} else if h.hasDebug(DebugOutRoam) {
			log.Info(ctx, "handle out", "iceId", h.IceId, "roam", iceCtx.Pack.Roam)
		}
	} else {
		log.Error(ctx, "root not exist", "iceId", h.IceId)
	}
}

// HandleWithConfId processes a context using conf ID.
func (h *Handler) HandleWithConfId(ctx stdctx.Context, iceCtx *icecontext.Context) {
	if h.hasDebug(DebugInPack) {
		log.Info(ctx, "handle confId in", "confId", h.ConfId, "pack", iceCtx.Pack)
	}

	if h.Root != nil {
		h.Root.Process(ctx, iceCtx)

		if h.hasDebug(DebugProcess) {
			log.Info(ctx, "handle confId process", "confId", h.ConfId, "process", iceCtx.ProcessInfo.String())
		}
		if h.hasDebug(DebugOutPack) {
			log.Info(ctx, "handle confId out", "confId", h.ConfId, "pack", iceCtx.Pack)
		} else if h.hasDebug(DebugOutRoam) {
			log.Info(ctx, "handle confId out", "confId", h.ConfId, "roam", iceCtx.Pack.Roam)
		}
	}
}

// SetScenes sets the scenes for this handler.
func (h *Handler) SetScenes(scenes []string) {
	h.Scenes = make(map[string]struct{}, len(scenes))
	for _, s := range scenes {
		h.Scenes[s] = struct{}{}
	}
}

// HasScene checks if the handler has the given scene.
func (h *Handler) HasScene(scene string) bool {
	_, ok := h.Scenes[scene]
	return ok
}

func (h *Handler) hasDebug(flag byte) bool {
	return (h.Debug & flag) != 0
}

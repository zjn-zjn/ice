// Package handler provides the ice handler implementation.
package handler

import (
	"encoding/json"
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/timeutil"
	"github.com/zjn-zjn/ice/sdks/go/log"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// DebugFlag constants for controlling debug output.
const (
	DebugInRoam  byte = 1 << 0 // Log input roam
	DebugProcess byte = 1 << 1 // Log execution process
	DebugOutRoam byte = 1 << 2 // Log output roam
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

// metaSuffix builds log args from meta: id/scene/nid/ts, only non-zero values.
func metaSuffix(roam *icecontext.Roam) []any {
	var args []any
	if roam.GetId() > 0 {
		args = append(args, "id", roam.GetId())
	}
	if roam.GetScene() != "" {
		args = append(args, "scene", roam.GetScene())
	}
	if roam.GetNid() > 0 {
		args = append(args, "nid", roam.GetNid())
	}
	args = append(args, "ts", roam.GetTs())
	return args
}

// roamWithoutIce returns JSON string of roam data excluding _ice.
func roamWithoutIce(roam *icecontext.Roam) string {
	data := roam.Data()
	delete(data, "_ice")
	b, _ := json.Marshal(data)
	return string(b)
}

// Handle processes a roam.
func (h *Handler) Handle(ctx stdctx.Context, roam *icecontext.Roam) {
	if timeutil.TimeDisabled(h.TimeType, roam.GetTs(), h.Start, h.End) {
		return
	}

	if h.hasDebug(DebugInRoam) {
		args := append([]any{"roam", roamWithoutIce(roam)}, metaSuffix(roam)...)
		log.Info(ctx, "handle in", args...)
	}

	if h.Root != nil {
		h.Root.Process(ctx, roam)

		if h.hasDebug(DebugProcess) {
			args := append([]any{"process", roam.GetProcess().String()}, metaSuffix(roam)...)
			log.Info(ctx, "handle process", args...)
		}
		if h.hasDebug(DebugOutRoam) {
			args := append([]any{"roam", roamWithoutIce(roam)}, metaSuffix(roam)...)
			log.Info(ctx, "handle out", args...)
		}
	} else {
		log.Error(ctx, "root not exist", metaSuffix(roam)...)
	}
}

// HandleWithNodeId processes a roam using node ID.
func (h *Handler) HandleWithNodeId(ctx stdctx.Context, roam *icecontext.Roam) {
	if h.hasDebug(DebugInRoam) {
		args := append([]any{"roam", roamWithoutIce(roam)}, metaSuffix(roam)...)
		log.Info(ctx, "handle in", args...)
	}

	if h.Root != nil {
		h.Root.Process(ctx, roam)

		if h.hasDebug(DebugProcess) {
			args := append([]any{"process", roam.GetProcess().String()}, metaSuffix(roam)...)
			log.Info(ctx, "handle process", args...)
		}
		if h.hasDebug(DebugOutRoam) {
			args := append([]any{"roam", roamWithoutIce(roam)}, metaSuffix(roam)...)
			log.Info(ctx, "handle out", args...)
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

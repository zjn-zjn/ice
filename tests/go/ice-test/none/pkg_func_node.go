package none

import (
	"context"
	"log/slog"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
)

// PkgFuncNone tests cross-function roam key detection.
// DoNone passes roam to a package-level function.
type PkgFuncNone struct{}

func (p *PkgFuncNone) DoNone(ctx context.Context, roam *icecontext.Roam) {
	logRoamField(roam, "a_b")
}

func logRoamField(roam *icecontext.Roam, key string) {
	val := roam.ValueDeep("x_y").String()
	slog.Info("pkg func", "key", key, "val", val)
}

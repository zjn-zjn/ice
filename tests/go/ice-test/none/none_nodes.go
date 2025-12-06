// Package none provides none leaf nodes for testing.
package none

import (
	"context"
	"log/slog"
	"time"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
)

// RoamProbeLogNone logs roam content for debugging.
type RoamProbeLogNone struct {
	Key string `json:"key"`
}

// DoRoamNone implements the RoamNone interface.
func (r *RoamProbeLogNone) DoRoamNone(ctx context.Context, roam *icecontext.Roam) {
	if r.Key != "" {
		slog.Info("roam probe", "key", r.Key, "value", roam.GetMulti(r.Key))
	} else {
		slog.Info("roam probe", "data", roam.Data())
	}
}

// TimeChangeNone modifies the request time for testing.
type TimeChangeNone struct {
	Time        *time.Time `json:"time,omitempty"`
	CursorMills int64      `json:"cursorMills"`
	Environment string     `json:"environment"`
}

// DoPackNone implements the PackNone interface.
func (t *TimeChangeNone) DoPackNone(ctx context.Context, pack *icecontext.Pack) {
	if t.Environment != "prod" {
		if t.Time != nil {
			pack.RequestTime = t.Time.UnixMilli()
		} else {
			pack.RequestTime = pack.RequestTime + t.CursorMills
		}
	}
}

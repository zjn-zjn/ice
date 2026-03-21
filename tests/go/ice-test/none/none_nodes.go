// Package none provides none leaf nodes for testing.
package none

import (
	"context"
	"log/slog"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
)

// RoamProbeLogNone logs roam content for debugging.
type RoamProbeLogNone struct {
	Key string `json:"key" ice:"name:探测键,desc:要输出的roam键名,为空则输出全部"`
}

// DoNone implements the LeafNone interface.
func (r *RoamProbeLogNone) DoNone(ctx context.Context, roam *icecontext.Roam) {
	if r.Key != "" {
		slog.Info("roam probe", "key", r.Key, "value", roam.GetDeep(r.Key))
	} else {
		slog.Info("roam probe", "data", roam.Data())
	}
}

// TimeChangeNone modifies the request time for testing.
type TimeChangeNone struct {
	CursorMills int64  `json:"cursorMills" ice:"name:时间偏移,desc:在当前请求时间上增加的毫秒数"`
	Environment string `json:"environment" ice:"name:环境,desc:限制生效的环境,prod环境不生效"`
}

// DoNone implements the LeafNone interface.
func (t *TimeChangeNone) DoNone(ctx context.Context, roam *icecontext.Roam) {
	if t.Environment != "prod" {
		meta := roam.GetMeta()
		if meta != nil {
			meta.Ts = meta.Ts + t.CursorMills
		}
	}
}

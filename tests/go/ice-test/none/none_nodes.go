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
	Key string `json:"key" ice:"name:探测键,desc:要输出的roam键名,为空则输出全部"`
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
	Time        *time.Time `json:"time,omitempty" ice:"name:指定时间,desc:将请求时间设置为指定时间"`
	CursorMills int64      `json:"cursorMills" ice:"name:时间偏移,desc:在当前请求时间上增加的毫秒数"`
	Environment string     `json:"environment" ice:"name:环境,desc:限制生效的环境,prod环境不生效"`
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

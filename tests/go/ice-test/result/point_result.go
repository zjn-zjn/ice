package result

import (
	"context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
)

// PointResult grants points to a user.
type PointResult struct {
	Key   string  `json:"key"`
	Value float64 `json:"value"`
}

// DoRoamResult implements the RoamResult interface.
func (p *PointResult) DoRoamResult(ctx context.Context, roam *icecontext.Roam) bool {
	uidVal := roam.GetMulti(p.Key)
	if uidVal == nil || p.Value <= 0 {
		return false
	}

	var uid int
	switch v := uidVal.(type) {
	case int:
		uid = v
	case int64:
		uid = int(v)
	case float64:
		uid = int(v)
	default:
		return false
	}

	res := sendService.SendPoint(uid, p.Value)
	roam.Put("SEND_POINT", res)
	return res
}

// PointResult2 is another variant of point granting.
type PointResult2 struct {
	Key   string  `json:"key"`
	Value float64 `json:"value"`
}

// DoRoamResult implements the RoamResult interface.
func (p *PointResult2) DoRoamResult(ctx context.Context, roam *icecontext.Roam) bool {
	uidVal := roam.GetMulti(p.Key)
	if uidVal == nil || p.Value <= 0 {
		return false
	}

	var uid int
	switch v := uidVal.(type) {
	case int:
		uid = v
	case int64:
		uid = int(v)
	case float64:
		uid = int(v)
	default:
		return false
	}

	res := sendService.SendPoint(uid, p.Value)
	roam.Put("SEND_POINT", res)
	return res
}

// InitConfigResult initializes config in roam.
type InitConfigResult struct {
	ConfigKey   string `json:"configKey"`
	ConfigValue string `json:"configValue"`
}

// DoRoamResult implements the RoamResult interface.
func (i *InitConfigResult) DoRoamResult(ctx context.Context, roam *icecontext.Roam) bool {
	if i.ConfigKey == "" {
		return false
	}
	roam.Put(i.ConfigKey, i.ConfigValue)
	return true
}

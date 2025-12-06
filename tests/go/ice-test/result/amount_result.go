// Package result provides result leaf nodes for testing.
package result

import (
	"context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/tests/go/ice-test/service"
)

var sendService = service.NewSendService()

// AmountResult grants amount to a user.
type AmountResult struct {
	Key   string  `json:"key"`
	Value float64 `json:"value"`
}

// DoRoamResult implements the RoamResult interface.
func (a *AmountResult) DoRoamResult(ctx context.Context, roam *icecontext.Roam) bool {
	uidVal := roam.GetMulti(a.Key)
	if uidVal == nil || a.Value <= 0 {
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

	res := sendService.SendAmount(uid, a.Value)
	roam.Put("SEND_AMOUNT", res)
	return res
}

// AmountResult2 is another variant of amount granting.
type AmountResult2 struct {
	Key   string  `json:"key"`
	Value float64 `json:"value"`
}

// DoRoamResult implements the RoamResult interface.
func (a *AmountResult2) DoRoamResult(ctx context.Context, roam *icecontext.Roam) bool {
	uidVal := roam.GetMulti(a.Key)
	if uidVal == nil || a.Value <= 0 {
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

	res := sendService.SendAmount(uid, a.Value)
	roam.Put("SEND_AMOUNT", res)
	return res
}

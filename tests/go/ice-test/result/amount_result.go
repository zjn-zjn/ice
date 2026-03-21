// Package result provides result leaf nodes for testing.
package result

import (
	"context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/tests/go/ice-test/service"
)

var sendService = service.NewSendService()

// AmountResult grants amount to a user.
type AmountResult struct {
	Key   string  `json:"key" ice:"name:用户ID键,desc:从roam中获取用户ID的键名"`
	Value float64 `json:"value" ice:"name:余额值,desc:要发放的余额数量"`
}

// DoResult implements the LeafResult interface.
func (a *AmountResult) DoResult(ctx context.Context, roam *icecontext.Roam) bool {
	uid := roam.ValueDeep(a.Key).Int()
	if uid == 0 || a.Value <= 0 {
		return false
	}

	res := sendService.SendAmount(uid, a.Value)
	roam.Put("SEND_AMOUNT", res)
	return res
}

// AmountResult2 is another variant of amount granting.
type AmountResult2 struct {
	Key   string  `json:"key" ice:"name:用户ID键,desc:从roam中获取用户ID的键名"`
	Value float64 `json:"value" ice:"name:余额值,desc:要发放的余额数量"`
}

// DoResult implements the LeafResult interface.
func (a *AmountResult2) DoResult(ctx context.Context, roam *icecontext.Roam) bool {
	uid := roam.ValueDeep(a.Key).Int()
	if uid == 0 || a.Value <= 0 {
		return false
	}

	res := sendService.SendAmount(uid, a.Value)
	roam.Put("SEND_AMOUNT", res)
	return res
}

package result

import (
	"context"

	ice "github.com/zjn-zjn/ice/sdks/go"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
)

func init() {
	ice.RegisterLeaf("com.ice.test.result.PointResult",
		&ice.LeafMeta{
			Name:  "发放积分节点",
			Desc:  "用于发放积分奖励",
			Alias: []string{"point_result"},
		},
		func() any { return &PointResult{} })

	ice.RegisterLeaf("com.ice.test.result.PointResult2",
		&ice.LeafMeta{
			Name:  "发放积分节点2",
			Desc:  "另一个积分发放",
			Alias: []string{"point_result_2"},
		},
		func() any { return &PointResult2{} })

	ice.RegisterLeaf("com.ice.test.result.InitConfigResult",
		&ice.LeafMeta{
			Name:  "初始化配置节点",
			Desc:  "初始化roam中的配置",
			Alias: []string{"init_config_result"},
		},
		func() any { return &InitConfigResult{} })
}

// PointResult grants points to a user.
type PointResult struct {
	Key   string  `json:"key" ice:"name:用户ID键,desc:从roam中获取用户ID的键名"`
	Value float64 `json:"value" ice:"name:积分值,desc:要发放的积分数量"`
}

// DoResult implements the LeafResult interface.
func (p *PointResult) DoResult(ctx context.Context, roam *icecontext.Roam) bool {
	uid := roam.ValueDeep(p.Key).Int()
	if uid == 0 || p.Value <= 0 {
		return false
	}

	res := sendService.SendPoint(uid, p.Value)
	roam.Put("SEND_POINT", res)
	return res
}

// PointResult2 is another variant of point granting.
type PointResult2 struct {
	Key   string  `json:"key" ice:"name:用户ID键,desc:从roam中获取用户ID的键名"`
	Value float64 `json:"value" ice:"name:积分值,desc:要发放的积分数量"`
}

// DoResult implements the LeafResult interface.
func (p *PointResult2) DoResult(ctx context.Context, roam *icecontext.Roam) bool {
	uid := roam.ValueDeep(p.Key).Int()
	if uid == 0 || p.Value <= 0 {
		return false
	}

	res := sendService.SendPoint(uid, p.Value)
	roam.Put("SEND_POINT", res)
	return res
}

// InitConfigResult initializes config in roam.
type InitConfigResult struct {
	ConfigKey   string `json:"configKey" ice:"name:配置键,desc:要设置的配置键名"`
	ConfigValue string `json:"configValue" ice:"name:配置值,desc:要设置的配置值"`
}

// DoResult implements the LeafResult interface.
func (i *InitConfigResult) DoResult(ctx context.Context, roam *icecontext.Roam) bool {
	if i.ConfigKey == "" {
		return false
	}
	roam.Put(i.ConfigKey, i.ConfigValue)
	return true
}

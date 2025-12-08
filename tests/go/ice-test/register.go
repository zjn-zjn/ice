// Package main registers all leaf nodes.
package main

import (
	ice "github.com/waitmoon/ice/sdks/go"
	"github.com/waitmoon/ice/tests/go/ice-test/flow"
	"github.com/waitmoon/ice/tests/go/ice-test/none"
	"github.com/waitmoon/ice/tests/go/ice-test/result"
)

func registerLeafNodes() {
	// Register flow nodes
	ice.RegisterLeaf("com.ice.test.flow.ScoreFlow",
		&ice.LeafMeta{
			Name:  "分数判断节点",
			Desc:  "取出roam中的值比较大小",
			Alias: []string{"score_flow"},
		},
		func() any { return &flow.ScoreFlow{} })

	ice.RegisterLeaf("com.ice.test.flow.ScoreFlow2",
		&ice.LeafMeta{
			Name:  "分数判断节点2",
			Desc:  "另一个分数判断",
			Alias: []string{"score_flow_2"},
		},
		func() any { return &flow.ScoreFlow2{} })

	// Register result nodes
	ice.RegisterLeaf("com.ice.test.result.AmountResult",
		&ice.LeafMeta{
			Name:  "发放余额节点",
			Desc:  "用于发放余额",
			Alias: []string{"amount_result"},
		},
		func() any { return &result.AmountResult{} })

	ice.RegisterLeaf("com.ice.test.result.AmountResult2",
		&ice.LeafMeta{
			Name:  "发放余额节点2",
			Desc:  "另一个发放余额",
			Alias: []string{"amount_result_2"},
		},
		func() any { return &result.AmountResult2{} })

	ice.RegisterLeaf("com.ice.test.result.PointResult",
		&ice.LeafMeta{
			Name:  "发放积分节点",
			Desc:  "用于发放积分奖励",
			Alias: []string{"point_result"},
		},
		func() any { return &result.PointResult{} })

	ice.RegisterLeaf("com.ice.test.result.PointResult2",
		&ice.LeafMeta{
			Name:  "发放积分节点2",
			Desc:  "另一个积分发放",
			Alias: []string{"point_result_2"},
		},
		func() any { return &result.PointResult2{} })

	ice.RegisterLeaf("com.ice.test.result.InitConfigResult",
		&ice.LeafMeta{
			Name:  "初始化配置节点",
			Desc:  "初始化roam中的配置",
			Alias: []string{"init_config_result"},
		},
		func() any { return &result.InitConfigResult{} })

	// Register none nodes
	ice.RegisterLeaf("com.ice.test.none.RoamProbeLogNone",
		&ice.LeafMeta{
			Name:  "Roam探针日志",
			Desc:  "输出roam内容用于调试",
			Alias: []string{"roam_probe_log_none"},
		},
		func() any { return &none.RoamProbeLogNone{} })

	ice.RegisterLeaf("com.ice.test.none.TimeChangeNone",
		&ice.LeafMeta{
			Name:  "时间修改节点",
			Desc:  "用于测试时修改请求时间",
			Alias: []string{"time_change_none"},
		},
		func() any { return &none.TimeChangeNone{} })
}

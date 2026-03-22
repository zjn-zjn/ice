// Package flow provides flow leaf nodes for testing.
package flow

import (
	"context"

	ice "github.com/zjn-zjn/ice/sdks/go"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
)

func init() {
	ice.RegisterLeaf("com.ice.test.flow.ScoreFlow",
		&ice.LeafMeta{
			Name:  "分数判断节点",
			Desc:  "取出roam中的值比较大小",
			Alias: []string{"score_flow"},
		},
		func() any { return &ScoreFlow{} })

	ice.RegisterLeaf("com.ice.test.flow.ScoreFlow2",
		&ice.LeafMeta{
			Name:  "分数判断节点2",
			Desc:  "另一个分数判断",
			Alias: []string{"score_flow_2"},
		},
		func() any { return &ScoreFlow2{} })
}

// ScoreFlow checks if roam[key] >= score.
type ScoreFlow struct {
	Score float64 `json:"score" ice:"name:分数阈值,desc:判断分数的阈值"`
	Key   string  `json:"key" ice:"name:取值键,desc:从roam中取值的键名"`
}

// DoFlow implements the LeafFlow interface.
func (s *ScoreFlow) DoFlow(ctx context.Context, roam *icecontext.Roam) bool {
	value := roam.ValueDeep(s.Key).Float64Or(0)
	return value >= s.Score
}

// ScoreFlow2 is another variant of score checking.
type ScoreFlow2 struct {
	Score float64 `json:"score" ice:"name:分数阈值,desc:判断分数的阈值"`
	Key   string  `json:"key" ice:"name:取值键,desc:从roam中取值的键名"`
}

// DoFlow implements the LeafFlow interface.
func (s *ScoreFlow2) DoFlow(ctx context.Context, roam *icecontext.Roam) bool {
	value := roam.ValueDeep(s.Key).Float64Or(0)
	return value >= s.Score
}

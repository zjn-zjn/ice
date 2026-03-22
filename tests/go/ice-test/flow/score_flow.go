// Package flow provides flow leaf nodes for testing.
package flow

import (
	"context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
)

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

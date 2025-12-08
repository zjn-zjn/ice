// Package flow provides flow leaf nodes for testing.
package flow

import (
	"context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
)

// ScoreFlow checks if roam[key] >= score.
type ScoreFlow struct {
	Score float64 `json:"score" ice:"name:分数阈值,desc:判断分数的阈值"`
	Key   string  `json:"key" ice:"name:取值键,desc:从roam中取值的键名"`
}

// DoRoamFlow implements the RoamFlow interface.
func (s *ScoreFlow) DoRoamFlow(ctx context.Context, roam *icecontext.Roam) bool {
	value := roam.GetMulti(s.Key)
	if value == nil {
		return false
	}

	var valueScore float64
	switch v := value.(type) {
	case float64:
		valueScore = v
	case int:
		valueScore = float64(v)
	case int64:
		valueScore = float64(v)
	default:
		return false
	}

	return valueScore >= s.Score
}

// ScoreFlow2 is another variant of score checking.
type ScoreFlow2 struct {
	Score float64 `json:"score" ice:"name:分数阈值,desc:判断分数的阈值"`
	Key   string  `json:"key" ice:"name:取值键,desc:从roam中取值的键名"`
}

// DoRoamFlow implements the RoamFlow interface.
func (s *ScoreFlow2) DoRoamFlow(ctx context.Context, roam *icecontext.Roam) bool {
	value := roam.GetMulti(s.Key)
	if value == nil {
		return false
	}

	var valueScore float64
	switch v := value.(type) {
	case float64:
		valueScore = v
	case int:
		valueScore = float64(v)
	case int64:
		valueScore = float64(v)
	default:
		return false
	}

	return valueScore >= s.Score
}

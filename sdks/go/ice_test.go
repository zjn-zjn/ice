package ice

import (
	stdctx "context"
	"testing"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/leaf"
	"github.com/zjn-zjn/ice/sdks/go/node"
	"github.com/zjn-zjn/ice/sdks/go/relation"
)

// ScoreCheck is a sample leaf node for testing.
type ScoreCheck struct {
	Threshold int `json:"threshold"`
}

func (s *ScoreCheck) DoRoamFlow(ctx stdctx.Context, roam *icecontext.Roam) bool {
	return roam.GetInt("score", 0) >= s.Threshold
}

// SetResult is a sample result leaf node.
type SetResult struct {
	Key   string `json:"key"`
	Value string `json:"value"`
}

func (s *SetResult) DoRoamResult(ctx stdctx.Context, roam *icecontext.Roam) bool {
	roam.Put(s.Key, s.Value)
	return true
}

func init() {
	leaf.Register("com.example.ScoreCheck", nil, func() any {
		return &ScoreCheck{}
	})
	leaf.Register("com.example.SetResult", nil, func() any {
		return &SetResult{}
	})
}

func TestRoam(t *testing.T) {
	roam := NewRoam()
	roam.Put("name", "test")
	roam.Put("score", 85)

	if roam.GetString("name") != "test" {
		t.Error("expected name=test")
	}
	if roam.GetInt("score", 0) != 85 {
		t.Error("expected score=85")
	}

	// Test multi-key
	roam.PutMulti("user.profile.age", 25)
	if roam.GetMulti("user.profile.age") != 25 {
		t.Error("expected user.profile.age=25")
	}

	// Test GetUnion
	roam.Put("ref", "@score")
	if roam.GetUnion(roam.Get("ref")) != 85 {
		t.Error("expected @score to resolve to 85")
	}
}

func TestPack(t *testing.T) {
	pack := NewPack()
	pack.SetIceId(1).SetScene("test").SetDebug(1)

	if pack.IceId != 1 {
		t.Error("expected iceId=1")
	}
	if pack.Scene != "test" {
		t.Error("expected scene=test")
	}

	// Test clone
	clone := pack.Clone()
	clone.IceId = 2
	if pack.IceId != 1 {
		t.Error("clone should not affect original")
	}
}

func TestAndRelation(t *testing.T) {
	and := relation.NewAnd()
	and.IceNodeId = 1
	and.IceLogName = "TestAnd"

	ctx := stdctx.Background()
	iceCtx := NewContext(1, NewPack())

	// Empty children -> NONE
	result := and.Process(ctx, iceCtx)
	if result != enum.NONE {
		t.Errorf("expected NONE, got %v", result)
	}
}

func TestLeafRegistration(t *testing.T) {
	if !leaf.IsRegistered("com.example.ScoreCheck") {
		t.Error("ScoreCheck should be registered")
	}
	if !leaf.IsRegistered("com.example.SetResult") {
		t.Error("SetResult should be registered")
	}
	if leaf.IsRegistered("com.example.NotExist") {
		t.Error("NotExist should not be registered")
	}
}

func TestLeafNode(t *testing.T) {
	ctx := stdctx.Background()

	// Create a leaf node
	leafNode, err := leaf.CreateNode("com.example.ScoreCheck", `{"threshold": 80}`)
	if err != nil {
		t.Fatalf("failed to create leaf node: %v", err)
	}

	// Test with score >= threshold
	pack := NewPack()
	pack.Roam.Put("score", 85)
	iceCtx := NewContext(1, pack)

	result := leafNode.Process(ctx, iceCtx)
	if result != enum.TRUE {
		t.Errorf("expected TRUE for score=85, threshold=80, got %v", result)
	}

	// Test with score < threshold
	pack2 := NewPack()
	pack2.Roam.Put("score", 70)
	iceCtx2 := NewContext(1, pack2)

	result2 := leafNode.Process(ctx, iceCtx2)
	if result2 != enum.FALSE {
		t.Errorf("expected FALSE for score=70, threshold=80, got %v", result2)
	}
}

func TestNodeBase(t *testing.T) {
	and := relation.NewAnd()
	and.IceNodeId = 100
	and.IceLogName = "TestNode"
	and.IceInverse = true

	if and.GetNodeId() != 100 {
		t.Error("expected nodeId=100")
	}
	if and.GetLogName() != "TestNode" {
		t.Error("expected logName=TestNode")
	}
	if !and.IsInverse() {
		t.Error("expected inverse=true")
	}
}

func TestLinkedList(t *testing.T) {
	// Test relation children
	and := relation.NewAnd()

	child1 := relation.NewTrue()
	child1.IceNodeId = 1

	child2 := relation.NewTrue()
	child2.IceNodeId = 2

	and.Children.Add(child1)
	and.Children.Add(child2)

	if and.Children.Size() != 2 {
		t.Errorf("expected 2 children, got %d", and.Children.Size())
	}
}

func TestProcessWithBase(t *testing.T) {
	ctx := stdctx.Background()
	base := &node.Base{
		IceNodeId:    1,
		IceLogName:   "Test",
		IceNodeDebug: true,
	}

	iceCtx := NewContext(1, NewPack())

	called := false
	result := node.ProcessWithBase(ctx, base, iceCtx, func(c stdctx.Context, ic *icecontext.Context) enum.RunState {
		called = true
		return enum.TRUE
	}, nil)

	if !called {
		t.Error("processNode should have been called")
	}
	if result != enum.TRUE {
		t.Errorf("expected TRUE, got %v", result)
	}

	// Check debug info was collected
	if iceCtx.ProcessInfo.Len() == 0 {
		t.Error("expected debug info to be collected")
	}
}

func TestPackString(t *testing.T) {
	pack := NewPack()
	pack.SetIceId(1).SetScene("test")
	pack.Roam.Put("key", "value")

	str := pack.String()
	if str == "" || str == "{}" {
		t.Error("expected non-empty JSON string")
	}
}

func TestRoamString(t *testing.T) {
	roam := NewRoam()
	roam.Put("name", "test")

	str := roam.String()
	if str == "" || str == "{}" {
		t.Error("expected non-empty JSON string")
	}
}

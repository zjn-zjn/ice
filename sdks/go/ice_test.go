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

func (s *ScoreCheck) DoFlow(ctx stdctx.Context, roam *icecontext.Roam) bool {
	return roam.Value("score").IntOr(0) >= s.Threshold
}

// SetResult is a sample result leaf node.
type SetResult struct {
	Key   string `json:"key"`
	Value string `json:"value"`
}

func (s *SetResult) DoResult(ctx stdctx.Context, roam *icecontext.Roam) bool {
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

	if roam.Value("name").String() != "test" {
		t.Error("expected name=test")
	}
	if roam.Value("score").IntOr(0) != 85 {
		t.Error("expected score=85")
	}

	// Test deep-key
	roam.PutDeep("user.profile.age", 25)
	if roam.GetDeep("user.profile.age") != 25 {
		t.Error("expected user.profile.age=25")
	}

	// Test Resolve
	roam.Put("ref", "@score")
	if roam.Resolve(roam.Get("ref")) != 85 {
		t.Error("expected @score to resolve to 85")
	}
}

func TestRoamWithMeta(t *testing.T) {
	roam := NewRoam()
	ice := roam.GetMeta()
	if ice == nil {
		t.Fatal("expected meta to be non-nil")
	}
	roam.SetId(1)
	roam.SetScene("test")
	roam.SetDebug(1)

	if roam.GetId() != 1 {
		t.Error("expected iceId=1")
	}
	if roam.GetScene() != "test" {
		t.Error("expected scene=test")
	}

	// Test clone
	clone := roam.Clone()
	clone.SetId(2)
	if roam.GetId() != 1 {
		t.Error("clone should not affect original")
	}
}

func TestAndRelation(t *testing.T) {
	and := relation.NewAnd()
	and.IceNodeId = 1
	and.IceLogName = "TestAnd"

	ctx := stdctx.Background()
	roam := NewRoam()

	// Empty children -> NONE
	result := and.Process(ctx, roam)
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
	roam := NewRoam()
	roam.Put("score", 85)

	result := leafNode.Process(ctx, roam)
	if result != enum.TRUE {
		t.Errorf("expected TRUE for score=85, threshold=80, got %v", result)
	}

	// Test with score < threshold
	roam2 := NewRoam()
	roam2.Put("score", 70)

	result2 := leafNode.Process(ctx, roam2)
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
		IceNodeId:  1,
		IceLogName: "Test",
	}

	roam := NewRoam()

	called := false
	result := node.ProcessWithBase(ctx, base, roam, func(c stdctx.Context, r *icecontext.Roam) enum.RunState {
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
	if roam.GetProcess().Len() == 0 {
		t.Error("expected debug info to be collected")
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

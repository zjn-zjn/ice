package relation

import (
	stdctx "context"
	"testing"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// mockNode is a test node that returns a fixed state
type mockNode struct {
	node.Base
	state enum.RunState
}

func (m *mockNode) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return m.state
}

func newMockNode(id int64, state enum.RunState) *mockNode {
	return &mockNode{
		Base:  node.Base{IceNodeId: id, IceLogName: "mock"},
		state: state,
	}
}

func TestAnd_EmptyChildren(t *testing.T) {
	and := NewAnd()
	and.IceNodeId = 1
	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())

	result := and.Process(ctx, iceCtx)
	if result != enum.NONE {
		t.Errorf("expected NONE for empty children, got %v", result)
	}
}

func TestAnd_AllTrue(t *testing.T) {
	and := NewAnd()
	and.IceNodeId = 1
	and.Children.Add(newMockNode(2, enum.TRUE))
	and.Children.Add(newMockNode(3, enum.TRUE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := and.Process(ctx, iceCtx)
	if result != enum.TRUE {
		t.Errorf("expected TRUE, got %v", result)
	}
}

func TestAnd_HasFalse(t *testing.T) {
	and := NewAnd()
	and.IceNodeId = 1
	and.Children.Add(newMockNode(2, enum.TRUE))
	and.Children.Add(newMockNode(3, enum.FALSE))
	and.Children.Add(newMockNode(4, enum.TRUE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := and.Process(ctx, iceCtx)
	if result != enum.FALSE {
		t.Errorf("expected FALSE (short-circuit), got %v", result)
	}
}

func TestAnd_AllNone(t *testing.T) {
	and := NewAnd()
	and.IceNodeId = 1
	and.Children.Add(newMockNode(2, enum.NONE))
	and.Children.Add(newMockNode(3, enum.NONE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := and.Process(ctx, iceCtx)
	if result != enum.NONE {
		t.Errorf("expected NONE, got %v", result)
	}
}

func TestAny_EmptyChildren(t *testing.T) {
	any := NewAny()
	any.IceNodeId = 1
	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())

	result := any.Process(ctx, iceCtx)
	if result != enum.NONE {
		t.Errorf("expected NONE for empty children, got %v", result)
	}
}

func TestAny_HasTrue(t *testing.T) {
	any := NewAny()
	any.IceNodeId = 1
	any.Children.Add(newMockNode(2, enum.FALSE))
	any.Children.Add(newMockNode(3, enum.TRUE))
	any.Children.Add(newMockNode(4, enum.FALSE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := any.Process(ctx, iceCtx)
	if result != enum.TRUE {
		t.Errorf("expected TRUE (short-circuit), got %v", result)
	}
}

func TestAny_AllFalse(t *testing.T) {
	any := NewAny()
	any.IceNodeId = 1
	any.Children.Add(newMockNode(2, enum.FALSE))
	any.Children.Add(newMockNode(3, enum.FALSE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := any.Process(ctx, iceCtx)
	if result != enum.FALSE {
		t.Errorf("expected FALSE, got %v", result)
	}
}

func TestAll_ExecutesAll(t *testing.T) {
	all := NewAll()
	all.IceNodeId = 1

	// Use mock nodes
	all.Children.Add(newMockNode(2, enum.TRUE))
	all.Children.Add(newMockNode(3, enum.FALSE))
	all.Children.Add(newMockNode(4, enum.TRUE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := all.Process(ctx, iceCtx)

	// Should return TRUE because there's at least one TRUE
	if result != enum.TRUE {
		t.Errorf("expected TRUE, got %v", result)
	}
}

func TestAll_OnlyFalse(t *testing.T) {
	all := NewAll()
	all.IceNodeId = 1
	all.Children.Add(newMockNode(2, enum.FALSE))
	all.Children.Add(newMockNode(3, enum.FALSE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := all.Process(ctx, iceCtx)
	if result != enum.FALSE {
		t.Errorf("expected FALSE, got %v", result)
	}
}

func TestTrue_AlwaysReturnsTrue(t *testing.T) {
	tr := NewTrue()
	tr.IceNodeId = 1
	tr.Children.Add(newMockNode(2, enum.FALSE))
	tr.Children.Add(newMockNode(3, enum.FALSE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := tr.Process(ctx, iceCtx)
	if result != enum.TRUE {
		t.Errorf("expected TRUE, got %v", result)
	}
}

func TestTrue_EmptyReturnsTrue(t *testing.T) {
	tr := NewTrue()
	tr.IceNodeId = 1

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := tr.Process(ctx, iceCtx)
	if result != enum.TRUE {
		t.Errorf("expected TRUE for empty children, got %v", result)
	}
}

func TestNone_AlwaysReturnsNone(t *testing.T) {
	none := NewNone()
	none.IceNodeId = 1
	none.Children.Add(newMockNode(2, enum.TRUE))
	none.Children.Add(newMockNode(3, enum.FALSE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := none.Process(ctx, iceCtx)
	if result != enum.NONE {
		t.Errorf("expected NONE, got %v", result)
	}
}

func TestRelation_Inverse(t *testing.T) {
	and := NewAnd()
	and.IceNodeId = 1
	and.IceInverse = true
	and.Children.Add(newMockNode(2, enum.TRUE))

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, icecontext.NewPack())
	result := and.Process(ctx, iceCtx)
	if result != enum.FALSE {
		t.Errorf("expected FALSE (inversed TRUE), got %v", result)
	}
}

func TestRelation_TimeDisabled(t *testing.T) {
	and := NewAnd()
	and.IceNodeId = 1
	and.IceTimeType = enum.TimeBetween
	and.IceStart = 100
	and.IceEnd = 200

	pack := icecontext.NewPack()
	pack.RequestTime = 50 // Before start

	ctx := stdctx.Background()
	iceCtx := icecontext.NewContext(1, pack)
	result := and.Process(ctx, iceCtx)
	if result != enum.NONE {
		t.Errorf("expected NONE (time disabled), got %v", result)
	}
}

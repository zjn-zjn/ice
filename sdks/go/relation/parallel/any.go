package parallel

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/executor"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// Any is a parallel relation node that returns TRUE on the first TRUE.
// Children are executed in parallel.
type Any struct {
	node.Relation
}

// NewAny creates a new parallel Any relation node.
func NewAny() *Any {
	a := &Any{
		Relation: *node.NewRelation(),
	}
	a.IceLogName = "P-Any"
	return a
}

// Process implements the Node interface.
func (a *Any) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &a.Base, iceCtx, a.processNode, nil)
}

func (a *Any) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if a.Children == nil || a.Children.IsEmpty() {
		return enum.NONE
	}

	if a.Children.Size() == 1 {
		n := a.Children.Get(0)
		if n == nil {
			return enum.NONE
		}
		return n.Process(ctx, iceCtx)
	}

	// Submit all children for parallel execution
	type pair struct {
		nodeId int64
		ch     <-chan executor.NodeResult
	}
	pairs := make([]pair, 0, a.Children.Size())

	for listNode := a.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			ch := executor.SubmitNode(ctx, n, iceCtx)
			pairs = append(pairs, pair{nodeId: n.GetNodeId(), ch: ch})
		}
	}

	hasFalse := false
	for _, p := range pairs {
		result := <-p.ch
		if result.State == enum.TRUE {
			return enum.TRUE
		}
		if !hasFalse && result.State == enum.FALSE {
			hasFalse = true
		}
	}

	if hasFalse {
		return enum.FALSE
	}
	return enum.NONE
}

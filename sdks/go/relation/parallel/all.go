package parallel

import (
	stdctx "context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/internal/executor"
	"github.com/waitmoon/ice/sdks/go/node"
)

// All is a parallel relation node that executes all children.
// Children are executed in parallel.
type All struct {
	node.Relation
}

// NewAll creates a new parallel All relation node.
func NewAll() *All {
	a := &All{
		Relation: *node.NewRelation(),
	}
	a.IceLogName = "P-All"
	return a
}

// Process implements the Node interface.
func (a *All) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &a.Base, iceCtx, a.processNode)
}

func (a *All) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
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

	hasTrue := false
	hasFalse := false
	for _, p := range pairs {
		result := <-p.ch
		if !hasTrue && result.State == enum.TRUE {
			hasTrue = true
		}
		if !hasFalse && result.State == enum.FALSE {
			hasFalse = true
		}
	}

	if hasTrue {
		return enum.TRUE
	}
	if hasFalse {
		return enum.FALSE
	}
	return enum.NONE
}

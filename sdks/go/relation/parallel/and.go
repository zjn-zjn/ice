// Package parallel provides parallel relation node implementations.
package parallel

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/executor"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// And is a parallel relation node that returns FALSE on the first FALSE.
// Children are executed in parallel.
type And struct {
	node.Relation
}

// NewAnd creates a new parallel And relation node.
func NewAnd() *And {
	a := &And{
		Relation: *node.NewRelation(),
	}
	a.IceLogName = "P-And"
	return a
}

// Process implements the Node interface.
func (a *And) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &a.Base, iceCtx, a.processNode)
}

func (a *And) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
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
	for _, p := range pairs {
		result := <-p.ch
		if result.State == enum.FALSE {
			return enum.FALSE
		}
		if !hasTrue && result.State == enum.TRUE {
			hasTrue = true
		}
	}

	if hasTrue {
		return enum.TRUE
	}
	return enum.NONE
}

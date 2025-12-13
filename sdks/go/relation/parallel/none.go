package parallel

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/executor"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// None is a parallel relation node that executes all children and returns NONE.
// Children are executed in parallel.
type None struct {
	node.Relation
}

// NewNone creates a new parallel None relation node.
func NewNone() *None {
	n := &None{
		Relation: *node.NewRelation(),
	}
	n.IceLogName = "P-None"
	return n
}

// Process implements the Node interface.
func (n *None) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &n.Base, iceCtx, n.processNode, nil)
}

func (n *None) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if n.Children == nil || n.Children.IsEmpty() {
		return enum.NONE
	}

	if n.Children.Size() == 1 {
		child := n.Children.Get(0)
		if child != nil {
			child.Process(ctx, iceCtx)
		}
		return enum.NONE
	}

	// Submit all children for parallel execution
	channels := make([]<-chan executor.NodeResult, 0, n.Children.Size())

	for listNode := n.Children.First(); listNode != nil; listNode = listNode.Next {
		child := listNode.Item
		if child != nil {
			ch := executor.SubmitNode(ctx, child, iceCtx)
			channels = append(channels, ch)
		}
	}

	// Wait for all to complete
	for _, ch := range channels {
		<-ch
	}

	return enum.NONE
}

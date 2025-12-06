package parallel

import (
	stdctx "context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/internal/executor"
	"github.com/waitmoon/ice/sdks/go/node"
)

// True is a parallel relation node that executes all children and returns TRUE.
// Children are executed in parallel.
type True struct {
	node.Relation
}

// NewTrue creates a new parallel True relation node.
func NewTrue() *True {
	t := &True{
		Relation: *node.NewRelation(),
	}
	t.IceLogName = "P-True"
	return t
}

// Process implements the Node interface.
func (t *True) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &t.Base, iceCtx, t.processNode)
}

func (t *True) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if t.Children == nil || t.Children.IsEmpty() {
		return enum.TRUE
	}

	if t.Children.Size() == 1 {
		n := t.Children.Get(0)
		if n != nil {
			n.Process(ctx, iceCtx)
		}
		return enum.TRUE
	}

	// Submit all children for parallel execution
	channels := make([]<-chan executor.NodeResult, 0, t.Children.Size())

	for listNode := t.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			ch := executor.SubmitNode(ctx, n, iceCtx)
			channels = append(channels, ch)
		}
	}

	// Wait for all to complete
	for _, ch := range channels {
		<-ch
	}

	return enum.TRUE
}

package relation

import (
	stdctx "context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/node"
)

// True is a relation node that executes all children and always returns TRUE.
type True struct {
	node.Relation
}

// NewTrue creates a new True relation node.
func NewTrue() *True {
	return &True{
		Relation: *node.NewRelation(),
	}
}

// Process implements the Node interface.
func (t *True) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &t.Base, iceCtx, t.processNode)
}

func (t *True) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if t.Children == nil || t.Children.IsEmpty() {
		return enum.TRUE
	}

	for listNode := t.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			n.Process(ctx, iceCtx)
		}
	}

	return enum.TRUE
}

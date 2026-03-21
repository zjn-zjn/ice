package relation

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/node"
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
func (t *True) Process(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	return node.ProcessWithBase(ctx, &t.Base, roam, t.processNode, nil)
}

func (t *True) processNode(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	if t.Children == nil || t.Children.IsEmpty() {
		return enum.TRUE
	}

	for listNode := t.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			n.Process(ctx, roam)
		}
	}

	return enum.TRUE
}

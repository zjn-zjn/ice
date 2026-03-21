package relation

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// None is a relation node that executes all children and always returns NONE.
type None struct {
	node.Relation
}

// NewNone creates a new None relation node.
func NewNone() *None {
	return &None{
		Relation: *node.NewRelation(),
	}
}

// Process implements the Node interface.
func (n *None) Process(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	return node.ProcessWithBase(ctx, &n.Base, roam, n.processNode, nil)
}

func (n *None) processNode(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	if n.Children == nil || n.Children.IsEmpty() {
		return enum.NONE
	}

	for listNode := n.Children.First(); listNode != nil; listNode = listNode.Next {
		child := listNode.Item
		if child != nil {
			child.Process(ctx, roam)
		}
	}

	return enum.NONE
}

package relation

import (
	stdctx "context"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/node"
)

// Any is a relation node that returns TRUE on the first TRUE.
// - Has TRUE -> TRUE
// - Without TRUE, has FALSE -> FALSE
// - Without children -> NONE
// - All NONE -> NONE
type Any struct {
	node.Relation
}

// NewAny creates a new Any relation node.
func NewAny() *Any {
	return &Any{
		Relation: *node.NewRelation(),
	}
}

// Process implements the Node interface.
func (a *Any) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &a.Base, iceCtx, a.processNode)
}

func (a *Any) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if a.Children == nil || a.Children.IsEmpty() {
		return enum.NONE
	}

	hasFalse := false
	for listNode := a.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			state := n.Process(ctx, iceCtx)
			if state == enum.TRUE {
				return enum.TRUE
			}
			if !hasFalse && state == enum.FALSE {
				hasFalse = true
			}
		}
	}

	if hasFalse {
		return enum.FALSE
	}
	return enum.NONE
}

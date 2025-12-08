package relation

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// All is a relation node that executes all children.
// - Has TRUE -> TRUE
// - Without TRUE, has FALSE -> FALSE
// - Without children -> NONE
// - All NONE -> NONE
type All struct {
	node.Relation
}

// NewAll creates a new All relation node.
func NewAll() *All {
	return &All{
		Relation: *node.NewRelation(),
	}
}

// Process implements the Node interface.
func (a *All) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &a.Base, iceCtx, a.processNode)
}

func (a *All) processNode(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if a.Children == nil || a.Children.IsEmpty() {
		return enum.NONE
	}

	hasTrue := false
	hasFalse := false
	for listNode := a.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			state := n.Process(ctx, iceCtx)
			if !hasTrue && state == enum.TRUE {
				hasTrue = true
			}
			if !hasFalse && state == enum.FALSE {
				hasFalse = true
			}
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

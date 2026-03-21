// Package relation provides relation node implementations.
package relation

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// And is a relation node that returns FALSE on the first FALSE.
// - Has FALSE -> FALSE
// - Without FALSE, has TRUE -> TRUE
// - Without children -> NONE
// - All NONE -> NONE
type And struct {
	node.Relation
}

// NewAnd creates a new And relation node.
func NewAnd() *And {
	return &And{
		Relation: *node.NewRelation(),
	}
}

// Process implements the Node interface.
func (a *And) Process(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	return node.ProcessWithBase(ctx, &a.Base, roam, a.processNode, nil)
}

func (a *And) processNode(ctx stdctx.Context, roam *icecontext.Roam) enum.RunState {
	if a.Children == nil || a.Children.IsEmpty() {
		return enum.NONE
	}

	hasTrue := false
	for listNode := a.Children.First(); listNode != nil; listNode = listNode.Next {
		n := listNode.Item
		if n != nil {
			state := n.Process(ctx, roam)
			if state == enum.FALSE {
				return enum.FALSE
			}
			if !hasTrue && state == enum.TRUE {
				hasTrue = true
			}
		}
	}

	if hasTrue {
		return enum.TRUE
	}
	return enum.NONE
}

package node

import (
	stdctx "context"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
)

// Leaf is the base type for leaf nodes.
type Leaf struct {
	Base
}

// LeafProcessor is implemented by leaf nodes to provide their specific logic.
type LeafProcessor interface {
	DoLeaf(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState
}

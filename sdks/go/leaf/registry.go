// Package leaf provides leaf node registration and auto-adaptation.
package leaf

import (
	stdctx "context"
	"encoding/json"
	"fmt"
	"sync"

	icecontext "github.com/waitmoon/ice/sdks/go/context"
	"github.com/waitmoon/ice/sdks/go/dto"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/node"
)

// LeafMeta contains metadata about a leaf node.
type LeafMeta struct {
	Name  string
	Desc  string
	Order int
}

// LeafFlow is implemented by flow-type leaf nodes with Context parameter.
type LeafFlow interface {
	DoFlow(ctx stdctx.Context, iceCtx *icecontext.Context) bool
}

// LeafPackFlow is implemented by flow-type leaf nodes with Pack parameter.
type LeafPackFlow interface {
	DoPackFlow(ctx stdctx.Context, pack *icecontext.Pack) bool
}

// LeafRoamFlow is implemented by flow-type leaf nodes with Roam parameter.
type LeafRoamFlow interface {
	DoRoamFlow(ctx stdctx.Context, roam *icecontext.Roam) bool
}

// LeafResult is implemented by result-type leaf nodes with Context parameter.
type LeafResult interface {
	DoResult(ctx stdctx.Context, iceCtx *icecontext.Context) bool
}

// LeafPackResult is implemented by result-type leaf nodes with Pack parameter.
type LeafPackResult interface {
	DoPackResult(ctx stdctx.Context, pack *icecontext.Pack) bool
}

// LeafRoamResult is implemented by result-type leaf nodes with Roam parameter.
type LeafRoamResult interface {
	DoRoamResult(ctx stdctx.Context, roam *icecontext.Roam) bool
}

// LeafNone is implemented by none-type leaf nodes with Context parameter.
type LeafNone interface {
	DoNone(ctx stdctx.Context, iceCtx *icecontext.Context)
}

// LeafPackNone is implemented by none-type leaf nodes with Pack parameter.
type LeafPackNone interface {
	DoPackNone(ctx stdctx.Context, pack *icecontext.Pack)
}

// LeafRoamNone is implemented by none-type leaf nodes with Roam parameter.
type LeafRoamNone interface {
	DoRoamNone(ctx stdctx.Context, roam *icecontext.Roam)
}

// leafEntry holds registration info for a leaf type.
type leafEntry struct {
	meta     *LeafMeta
	factory  func() any
	nodeType enum.NodeType
}

// LeafNode is a node that can have its base properties set.
type LeafNode interface {
	node.Node
	node.BaseAccessor
	SetBase(base *node.Base)
}

var (
	registry = make(map[string]*leafEntry)
	mu       sync.RWMutex
)

// Register registers a leaf node factory with the given class name.
// The factory should return a pointer to a struct that implements one of:
// - LeafFlow, LeafPackFlow, LeafRoamFlow (for flow type)
// - LeafResult, LeafPackResult, LeafRoamResult (for result type)
// - LeafNone, LeafPackNone, LeafRoamNone (for none type)
func Register(className string, meta *LeafMeta, factory func() any) {
	if className == "" || factory == nil {
		return
	}

	// Create a sample to detect the type
	sample := factory()
	nodeType := detectNodeType(sample)

	mu.Lock()
	defer mu.Unlock()
	registry[className] = &leafEntry{
		meta:     meta,
		factory:  factory,
		nodeType: nodeType,
	}
}

// detectNodeType detects the node type based on the interfaces implemented.
func detectNodeType(leaf any) enum.NodeType {
	// Check Flow types
	if _, ok := leaf.(LeafRoamFlow); ok {
		return enum.TypeLeafFlow
	}
	if _, ok := leaf.(LeafPackFlow); ok {
		return enum.TypeLeafFlow
	}
	if _, ok := leaf.(LeafFlow); ok {
		return enum.TypeLeafFlow
	}
	// Check Result types
	if _, ok := leaf.(LeafRoamResult); ok {
		return enum.TypeLeafResult
	}
	if _, ok := leaf.(LeafPackResult); ok {
		return enum.TypeLeafResult
	}
	if _, ok := leaf.(LeafResult); ok {
		return enum.TypeLeafResult
	}
	// Check None types
	if _, ok := leaf.(LeafRoamNone); ok {
		return enum.TypeLeafNone
	}
	if _, ok := leaf.(LeafPackNone); ok {
		return enum.TypeLeafNone
	}
	if _, ok := leaf.(LeafNone); ok {
		return enum.TypeLeafNone
	}
	return enum.TypeLeafNone
}

// CreateNode creates a leaf node from configuration.
func CreateNode(confName, confField string) (node.Node, error) {
	mu.RLock()
	entry, ok := registry[confName]
	mu.RUnlock()

	if !ok {
		return nil, fmt.Errorf("leaf class not found: %s", confName)
	}

	// Create instance
	instance := entry.factory()

	// Apply configuration
	if confField != "" && confField != "{}" {
		if err := json.Unmarshal([]byte(confField), instance); err != nil {
			return nil, fmt.Errorf("failed to unmarshal leaf config: %w", err)
		}
	}

	// Wrap with appropriate adapter
	return wrapLeaf(instance, entry.nodeType), nil
}

// wrapLeaf wraps a leaf instance with the appropriate node wrapper.
func wrapLeaf(instance any, nodeType enum.NodeType) node.Node {
	switch nodeType {
	case enum.TypeLeafFlow:
		return &flowLeafNode{instance: instance}
	case enum.TypeLeafResult:
		return &resultLeafNode{instance: instance}
	case enum.TypeLeafNone:
		return &noneLeafNode{instance: instance}
	default:
		return &noneLeafNode{instance: instance}
	}
}

// flowLeafNode wraps a flow-type leaf.
type flowLeafNode struct {
	node.Leaf
	instance any
}

// SetBase sets the base properties for this leaf node.
func (f *flowLeafNode) SetBase(base *node.Base) {
	f.Base = *base
}

func (f *flowLeafNode) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &f.Base, iceCtx, f.doLeaf)
}

func (f *flowLeafNode) doLeaf(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	var result bool

	// Try most specific first (Roam), then Pack, then Context
	if leaf, ok := f.instance.(LeafRoamFlow); ok {
		result = leaf.DoRoamFlow(ctx, iceCtx.Pack.Roam)
	} else if leaf, ok := f.instance.(LeafPackFlow); ok {
		result = leaf.DoPackFlow(ctx, iceCtx.Pack)
	} else if leaf, ok := f.instance.(LeafFlow); ok {
		result = leaf.DoFlow(ctx, iceCtx)
	}

	if result {
		return enum.TRUE
	}
	return enum.FALSE
}

// resultLeafNode wraps a result-type leaf.
type resultLeafNode struct {
	node.Leaf
	instance any
}

// SetBase sets the base properties for this leaf node.
func (r *resultLeafNode) SetBase(base *node.Base) {
	r.Base = *base
}

func (r *resultLeafNode) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &r.Base, iceCtx, r.doLeaf)
}

func (r *resultLeafNode) doLeaf(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	var result bool

	if leaf, ok := r.instance.(LeafRoamResult); ok {
		result = leaf.DoRoamResult(ctx, iceCtx.Pack.Roam)
	} else if leaf, ok := r.instance.(LeafPackResult); ok {
		result = leaf.DoPackResult(ctx, iceCtx.Pack)
	} else if leaf, ok := r.instance.(LeafResult); ok {
		result = leaf.DoResult(ctx, iceCtx)
	}

	if result {
		return enum.TRUE
	}
	return enum.FALSE
}

// noneLeafNode wraps a none-type leaf.
type noneLeafNode struct {
	node.Leaf
	instance any
}

// SetBase sets the base properties for this leaf node.
func (n *noneLeafNode) SetBase(base *node.Base) {
	n.Base = *base
}

func (n *noneLeafNode) Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	return node.ProcessWithBase(ctx, &n.Base, iceCtx, n.doLeaf)
}

func (n *noneLeafNode) doLeaf(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState {
	if leaf, ok := n.instance.(LeafRoamNone); ok {
		leaf.DoRoamNone(ctx, iceCtx.Pack.Roam)
	} else if leaf, ok := n.instance.(LeafPackNone); ok {
		leaf.DoPackNone(ctx, iceCtx.Pack)
	} else if leaf, ok := n.instance.(LeafNone); ok {
		leaf.DoNone(ctx, iceCtx)
	}
	return enum.NONE
}

// GetLeafNodes returns information about all registered leaf nodes.
func GetLeafNodes() []dto.LeafNodeInfo {
	mu.RLock()
	defer mu.RUnlock()

	result := make([]dto.LeafNodeInfo, 0, len(registry))
	for className, entry := range registry {
		info := dto.LeafNodeInfo{
			Type:  byte(entry.nodeType),
			Clazz: className,
			Order: 100,
		}
		if entry.meta != nil {
			info.Name = entry.meta.Name
			info.Desc = entry.meta.Desc
			if entry.meta.Order > 0 {
				info.Order = entry.meta.Order
			}
		}
		result = append(result, info)
	}
	return result
}

// IsRegistered checks if a leaf class is registered.
func IsRegistered(className string) bool {
	mu.RLock()
	defer mu.RUnlock()
	_, ok := registry[className]
	return ok
}

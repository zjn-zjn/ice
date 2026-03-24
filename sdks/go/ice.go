// Package ice provides a rule engine framework for Go.
// It is compatible with the Java ice-core implementation.
package ice

import (
	stdctx "context"

	"github.com/zjn-zjn/ice/sdks/go/cache"
	"github.com/zjn-zjn/ice/sdks/go/client"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/handler"
	"github.com/zjn-zjn/ice/sdks/go/internal/executor"
	"github.com/zjn-zjn/ice/sdks/go/leaf"
	icelog "github.com/zjn-zjn/ice/sdks/go/log"
	"github.com/zjn-zjn/ice/sdks/go/node"
)

// Re-export types for convenience
type (
	// Roam is the data container and execution context.
	Roam = icecontext.Roam
	// RunState represents the execution result.
	RunState = enum.RunState
	// Handler is the ice handler.
	Handler = handler.Handler
	// FileClient is the file-based client.
	FileClient = client.FileClient
	// LeafMeta contains metadata for leaf nodes.
	LeafMeta = leaf.LeafMeta
	// AfterPropertiesSet is an optional interface for leaf node initialization.
	// Implement this interface to perform initialization after config is applied.
	AfterPropertiesSet = leaf.AfterPropertiesSet
	// LeafErrorHandler is an optional interface for custom error handling.
	// Implement this interface in leaf nodes to handle errors during execution.
	LeafErrorHandler = leaf.LeafErrorHandler
	// GlobalErrorHandler is the type for the global error handler function.
	GlobalErrorHandler = node.GlobalErrorHandler
)

// Re-export constants
const (
	FALSE     = enum.FALSE
	TRUE      = enum.TRUE
	NONE      = enum.NONE
	SHUT_DOWN = enum.SHUT_DOWN
)

// Re-export constructors
var (
	// NewRoam creates a new Roam with default _ice metadata. Optional scene parameter.
	NewRoam = icecontext.NewRoam
	// NewClient creates a new FileClient. Optional lane parameter for swimlane support.
	NewClient = client.New
	// NewClientWithOptions creates a new FileClient with custom options.
	NewClientWithOptions = client.NewWithOptions
	// RegisterLeaf registers a leaf node factory.
	RegisterLeaf = leaf.Register
	// SetLogger sets the global logger.
	SetLogger = icelog.SetLogger
	// WithTraceId adds trace ID to context.
	WithTraceId = icelog.WithTraceId
	// GetTraceId gets trace ID from context.
	GetTraceId = icelog.GetTraceId
	// SetGlobalErrorHandler sets a custom global error handler.
	// This handler is called when a node does not implement LeafErrorHandler.
	// If not set, the default behavior is to return SHUT_DOWN (re-panic).
	SetGlobalErrorHandler = node.SetGlobalErrorHandler
)

// SyncProcess executes rules synchronously.
func SyncProcess(ctx stdctx.Context, roam *Roam) []*Roam {
	return syncDispatcher(ctx, roam)
}

// AsyncProcess executes rules asynchronously.
func AsyncProcess(ctx stdctx.Context, roam *Roam) []<-chan *Roam {
	return asyncDispatcher(ctx, roam)
}

// ProcessSingleRoam executes and returns a single roam result.
func ProcessSingleRoam(ctx stdctx.Context, roam *Roam) *Roam {
	roamList := SyncProcess(ctx, roam)
	if len(roamList) == 0 {
		return nil
	}
	return roamList[0]
}

// ProcessRoam executes and returns roam results.
func ProcessRoam(ctx stdctx.Context, roam *Roam) []*Roam {
	return SyncProcess(ctx, roam)
}

// GetHandlerById returns a handler by ID.
func GetHandlerById(iceId int64) *Handler {
	return cache.GetHandlerById(iceId)
}

// GetHandlersByScene returns handlers by scene.
func GetHandlersByScene(scene string) map[int64]*Handler {
	return cache.GetHandlersByScene(scene)
}

// InitExecutor initializes the executor pool.
func InitExecutor(size int) error {
	return executor.Init(size)
}

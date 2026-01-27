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
	// Context is the execution context.
	Context = icecontext.Context
	// Pack is the input pack for execution.
	Pack = icecontext.Pack
	// Roam is the data container.
	Roam = icecontext.Roam
	// RunState represents the execution result.
	RunState = enum.RunState
	// Handler is the ice handler.
	Handler = handler.Handler
	// FileClient is the file-based client.
	FileClient = client.FileClient
	// LeafMeta contains metadata for leaf nodes.
	LeafMeta = leaf.LeafMeta
	// Logger is the logging interface.
	Logger = icelog.Logger
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
	// NewPack creates a new Pack.
	NewPack = icecontext.NewPack
	// NewRoam creates a new Roam.
	NewRoam = icecontext.NewRoam
	// NewContext creates a new Context.
	NewContext = icecontext.NewContext
	// NewClient creates a new FileClient with minimal configuration (app, storagePath).
	NewClient = client.New
	// NewClientWithOptions creates a new FileClient with custom options.
	NewClientWithOptions = client.NewWithOptions
	// RegisterLeaf registers a leaf node factory.
	RegisterLeaf = leaf.Register
	// SetLogger sets the global logger.
	SetLogger = icelog.SetLogger
	// WithTraceId adds trace ID to context.
	WithTraceId = icelog.WithTraceId
	// WithSpanId adds span ID to context.
	WithSpanId = icelog.WithSpanId
	// GetTraceId gets trace ID from context.
	GetTraceId = icelog.GetTraceId
	// GetSpanId gets span ID from context.
	GetSpanId = icelog.GetSpanId
	// SetGlobalErrorHandler sets a custom global error handler.
	// This handler is called when a node does not implement LeafErrorHandler.
	// If not set, the default behavior is to return SHUT_DOWN (re-panic).
	SetGlobalErrorHandler = node.SetGlobalErrorHandler
)

// SyncProcess executes rules synchronously.
func SyncProcess(ctx stdctx.Context, pack *Pack) []*Context {
	return syncDispatcher(ctx, pack)
}

// AsyncProcess executes rules asynchronously.
func AsyncProcess(ctx stdctx.Context, pack *Pack) []<-chan *Context {
	return asyncDispatcher(ctx, pack)
}

// ProcessSingleRoam executes and returns a single roam result.
func ProcessSingleRoam(ctx stdctx.Context, pack *Pack) *Roam {
	iceCtx := ProcessSingleCtx(ctx, pack)
	if iceCtx != nil && iceCtx.Pack != nil {
		return iceCtx.Pack.Roam
	}
	return nil
}

// ProcessRoam executes and returns roam results.
func ProcessRoam(ctx stdctx.Context, pack *Pack) []*Roam {
	ctxList := SyncProcess(ctx, pack)
	if len(ctxList) == 0 {
		return nil
	}
	result := make([]*Roam, 0, len(ctxList))
	for _, iceCtx := range ctxList {
		if iceCtx.Pack != nil {
			result = append(result, iceCtx.Pack.Roam)
		}
	}
	return result
}

// ProcessSingleCtx executes and returns a single context.
func ProcessSingleCtx(ctx stdctx.Context, pack *Pack) *Context {
	ctxList := ProcessCtx(ctx, pack)
	if len(ctxList) == 0 {
		return nil
	}
	return ctxList[0]
}

// ProcessCtx executes and returns contexts.
func ProcessCtx(ctx stdctx.Context, pack *Pack) []*Context {
	return SyncProcess(ctx, pack)
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

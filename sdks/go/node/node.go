// Package node provides the base node types for ice.
package node

import (
	stdctx "context"
	"fmt"

	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/timeutil"
)

// Node is the interface that all ice nodes must implement.
type Node interface {
	Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState
	GetNodeId() int64
	GetLogName() string
	IsDebug() bool
	IsInverse() bool
}

// ErrorHandler is an optional interface that nodes can implement
// to provide custom error handling logic.
// If the node implements this interface, it will be called when an error occurs.
// The returned RunState determines the behavior:
//   - SHUT_DOWN or nil: re-panic the error
//   - TRUE/FALSE/NONE: use this state and continue execution
type ErrorHandler interface {
	ErrorHandle(ctx stdctx.Context, iceCtx *icecontext.Context, err error) enum.RunState
}

// GlobalErrorHandler is a function type for the global error handler.
// It receives the node, context, ice context, and the error.
// Returns the RunState to determine behavior (same as ErrorHandler).
type GlobalErrorHandler func(node Node, ctx stdctx.Context, iceCtx *icecontext.Context, err error) enum.RunState

var globalErrorHandler GlobalErrorHandler

// SetGlobalErrorHandler sets a custom global error handler.
// This handler is called when a node does not implement ErrorHandler interface.
// If not set, the default behavior is to return SHUT_DOWN (re-panic).
func SetGlobalErrorHandler(handler GlobalErrorHandler) {
	globalErrorHandler = handler
}

// BaseAccessor provides access to node's base properties.
// All nodes (relation and leaf) should implement this.
type BaseAccessor interface {
	GetBase() *Base
	SetForward(Node)
	GetForward() Node
}

// Base contains common fields for all node types.
type Base struct {
	IceNodeId     int64
	IceTimeType   enum.TimeType
	IceStart      int64
	IceEnd        int64
	IceNodeDebug  bool
	IceInverse    bool
	IceForward    Node
	IceErrorState *enum.RunState
	IceLogName    string
	IceType       enum.NodeType
}

// GetNodeId returns the node ID.
func (b *Base) GetNodeId() int64 {
	return b.IceNodeId
}

// GetLogName returns the log name.
func (b *Base) GetLogName() string {
	return b.IceLogName
}

// IsDebug returns whether debug is enabled for this node.
func (b *Base) IsDebug() bool {
	return b.IceNodeDebug
}

// IsInverse returns whether the result should be inverted.
func (b *Base) IsInverse() bool {
	return b.IceInverse
}

// GetBase returns a pointer to the base (for BaseAccessor interface).
func (b *Base) GetBase() *Base {
	return b
}

// SetForward sets the forward node.
func (b *Base) SetForward(forward Node) {
	b.IceForward = forward
}

// GetForward returns the forward node.
func (b *Base) GetForward() Node {
	return b.IceForward
}

// ProcessWithBase executes common node logic and delegates to the specific processNode function.
// This implements the template method pattern for Go.
// The errorHandler parameter is optional (can be nil) - if the node implements ErrorHandler,
// pass it here for custom error handling.
func ProcessWithBase(ctx stdctx.Context, base *Base, iceCtx *icecontext.Context, processNode func(stdctx.Context, *icecontext.Context) enum.RunState, errorHandler ErrorHandler) (result enum.RunState) {
	// Time check
	if timeutil.TimeDisabled(base.IceTimeType, iceCtx.Pack.RequestTime, base.IceStart, base.IceEnd) {
		CollectInfo(iceCtx.ProcessInfo, base, 'O', 0)
		return enum.NONE
	}

	start := currentTimeMillis()

	// Error handling wrapper - uses named return value
	defer func() {
		if r := recover(); r != nil {
			elapsed := currentTimeMillis() - start
			
			// Convert panic value to error
			var err error
			if e, ok := r.(error); ok {
				err = e
			} else {
				err = fmt.Errorf("%v", r)
			}
			
			// 1. First try node-level error handler
			var errorState enum.RunState = enum.SHUT_DOWN
			if errorHandler != nil {
				errorState = errorHandler.ErrorHandle(ctx, iceCtx, err)
			} else if globalErrorHandler != nil {
				// 2. Fall back to global error handler
				// Note: we don't have the node here, pass nil
				errorState = globalErrorHandler(nil, ctx, iceCtx, err)
			}
			
			// 3. Config-level error state has highest priority
			if base.IceErrorState != nil {
				errorState = *base.IceErrorState
			}
			
			if errorState != enum.SHUT_DOWN {
				result = errorState
				CollectInfo(iceCtx.ProcessInfo, base, stateToChar(result), elapsed)
				// Apply inverse even on error
				if base.IceInverse {
					result = inverse(result)
				}
				return // named return value will be returned
			}
			
			// SHUT_DOWN - re-panic
			CollectInfo(iceCtx.ProcessInfo, base, 'S', elapsed)
			panic(r)
		}
	}()

	// Forward check
	if base.IceForward != nil {
		forwardRes := base.IceForward.Process(ctx, iceCtx)
		if forwardRes == enum.FALSE {
			CollectRejectInfo(iceCtx.ProcessInfo, base)
			return enum.FALSE
		}
		result = processNode(ctx, iceCtx)
		// Forward combines like AND relation
		if forwardRes == enum.NONE {
			// result stays as is
		} else if result == enum.NONE {
			result = enum.TRUE
		}
	} else {
		result = processNode(ctx, iceCtx)
	}

	CollectInfo(iceCtx.ProcessInfo, base, stateToChar(result), currentTimeMillis()-start)

	// Inverse
	if base.IceInverse {
		result = inverse(result)
	}

	return result
}

// inverse inverts TRUE to FALSE and vice versa. NONE and SHUT_DOWN are unchanged.
func inverse(state enum.RunState) enum.RunState {
	switch state {
	case enum.TRUE:
		return enum.FALSE
	case enum.FALSE:
		return enum.TRUE
	default:
		return state
	}
}

func stateToChar(state enum.RunState) byte {
	switch state {
	case enum.FALSE:
		return 'F'
	case enum.TRUE:
		return 'T'
	case enum.NONE:
		return 'N'
	case enum.SHUT_DOWN:
		return 'S'
	default:
		return '?'
	}
}

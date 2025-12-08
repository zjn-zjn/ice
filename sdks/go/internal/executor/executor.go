// Package executor provides a goroutine pool for parallel execution.
package executor

import (
	stdctx "context"
	"sync"

	"github.com/panjf2000/ants/v2"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/enum"
)

// NodeResult holds the result of a node execution.
type NodeResult struct {
	State enum.RunState
	Err   error
}

var (
	pool     *ants.Pool
	poolOnce sync.Once
)

// Init initializes the executor pool with the given size.
// If size <= 0, uses the default pool size.
func Init(size int) error {
	var err error
	poolOnce.Do(func() {
		if size <= 0 {
			pool, err = ants.NewPool(ants.DefaultAntsPoolSize)
		} else {
			pool, err = ants.NewPool(size)
		}
	})
	return err
}

// getPool returns the pool, initializing it if necessary.
func getPool() *ants.Pool {
	if pool == nil {
		_ = Init(0)
	}
	return pool
}

// NodeProcessor is a function that processes a context and returns a state.
type NodeProcessor interface {
	Process(ctx stdctx.Context, iceCtx *icecontext.Context) enum.RunState
}

// SubmitNode submits a node for parallel execution and returns a channel for the result.
func SubmitNode(ctx stdctx.Context, node NodeProcessor, iceCtx *icecontext.Context) <-chan NodeResult {
	ch := make(chan NodeResult, 1)
	p := getPool()
	if p == nil {
		// Fallback to synchronous execution
		result := node.Process(ctx, iceCtx)
		ch <- NodeResult{State: result}
		return ch
	}

	err := p.Submit(func() {
		defer func() {
			if r := recover(); r != nil {
				ch <- NodeResult{State: enum.SHUT_DOWN, Err: nil}
			}
		}()
		result := node.Process(ctx, iceCtx)
		ch <- NodeResult{State: result}
	})
	if err != nil {
		// Fallback to synchronous execution
		result := node.Process(ctx, iceCtx)
		ch <- NodeResult{State: result}
	}
	return ch
}

// SubmitNodeWithDone submits a node for parallel execution with a done flag check.
func SubmitNodeWithDone(ctx stdctx.Context, node NodeProcessor, pCtx *icecontext.ParallelContext) <-chan NodeResult {
	ch := make(chan NodeResult, 1)
	p := getPool()
	if p == nil {
		if !pCtx.IsDone() {
			result := node.Process(ctx, pCtx.Ctx)
			ch <- NodeResult{State: result}
		} else {
			ch <- NodeResult{State: enum.NONE}
		}
		return ch
	}

	err := p.Submit(func() {
		defer func() {
			if r := recover(); r != nil {
				ch <- NodeResult{State: enum.SHUT_DOWN}
			}
		}()
		if !pCtx.IsDone() {
			result := node.Process(ctx, pCtx.Ctx)
			ch <- NodeResult{State: result}
		} else {
			ch <- NodeResult{State: enum.NONE}
		}
	})
	if err != nil {
		if !pCtx.IsDone() {
			result := node.Process(ctx, pCtx.Ctx)
			ch <- NodeResult{State: result}
		} else {
			ch <- NodeResult{State: enum.NONE}
		}
	}
	return ch
}

// Shutdown gracefully shuts down the executor pool.
func Shutdown() {
	if pool != nil {
		pool.Release()
	}
}

package context

import (
	"strings"
	"sync/atomic"
	"time"
)

// Context represents the execution context for ice processing.
type Context struct {
	// IceTime is the time when the context was created.
	IceTime int64
	// IceId is the handler ID being executed.
	IceId int64
	// Pack is the input pack.
	Pack *Pack
	// ProcessInfo collects debug information during execution.
	ProcessInfo *strings.Builder
}

// NewContext creates a new Context.
func NewContext(iceId int64, pack *Pack) *Context {
	if pack == nil {
		pack = NewPack()
	}
	return &Context{
		IceTime:     time.Now().UnixMilli(),
		IceId:       iceId,
		Pack:        pack,
		ProcessInfo: &strings.Builder{},
	}
}

// ParallelContext wraps a Context for parallel execution.
type ParallelContext struct {
	isDone atomic.Bool
	Ctx    *Context
}

// NewParallelContext creates a new ParallelContext.
func NewParallelContext(ctx *Context) *ParallelContext {
	return &ParallelContext{
		Ctx: ctx,
	}
}

// IsDone returns whether the parallel execution is done.
func (pc *ParallelContext) IsDone() bool {
	return pc.isDone.Load()
}

// SetDone marks the parallel execution as done.
func (pc *ParallelContext) SetDone() {
	pc.isDone.Store(true)
}

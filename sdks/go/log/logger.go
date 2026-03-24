// Package log provides logging utilities using Go's standard log/slog.
//
// Users can replace the default logger with their own *slog.Logger via SetLogger.
// Since slog is Go's standard logging facade, any logging library (zap, zerolog,
// logrus, etc.) can be integrated by providing a slog.Handler implementation.
package log

import (
	"context"
	"log/slog"
	"os"
	"runtime"
	"time"
)

// TraceKey is the context key for trace ID.
type TraceKey struct{}

var defaultLogger *slog.Logger = slog.New(
	slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}),
)

// SetLogger sets the default logger.
func SetLogger(l *slog.Logger) {
	if l != nil {
		defaultLogger = l
	}
}

// GetLogger returns the default logger.
func GetLogger() *slog.Logger {
	return defaultLogger
}

// traceArgs prepends traceId as a structured field if present in context.
func traceArgs(ctx context.Context, args []any) []any {
	if ctx != nil {
		if v, ok := ctx.Value(TraceKey{}).(string); ok && v != "" {
			return append([]any{"traceId", v}, args...)
		}
	}
	return args
}

func log(ctx context.Context, level slog.Level, msg string, args ...any) {
	if !defaultLogger.Enabled(ctx, level) {
		return
	}
	var pcs [1]uintptr
	runtime.Callers(3, pcs[:]) // skip runtime.Callers, log, Debug/Info/Warn/Error
	r := slog.NewRecord(time.Now(), level, msg, pcs[0])
	r.Add(traceArgs(ctx, args)...)
	_ = defaultLogger.Handler().Handle(ctx, r)
}

// Debug logs a debug message.
func Debug(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelDebug, msg, args...)
}

// Info logs an info message.
func Info(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelInfo, msg, args...)
}

// Warn logs a warning message.
func Warn(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelWarn, msg, args...)
}

// Error logs an error message.
func Error(ctx context.Context, msg string, args ...any) {
	log(ctx, slog.LevelError, msg, args...)
}

// WithTraceId returns a new context with the given trace ID.
func WithTraceId(ctx context.Context, traceId string) context.Context {
	return context.WithValue(ctx, TraceKey{}, traceId)
}

// GetTraceId returns the trace ID from context.
func GetTraceId(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	if v, ok := ctx.Value(TraceKey{}).(string); ok {
		return v
	}
	return ""
}

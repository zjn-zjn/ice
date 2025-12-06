// Package log provides logging interface and default implementation.
package log

import (
	"context"
	"log/slog"
	"os"
)

// TraceKey is the context key for trace ID.
type TraceKey struct{}

// SpanKey is the context key for span ID.
type SpanKey struct{}

// Logger is the interface for logging with context support.
type Logger interface {
	Debug(ctx context.Context, msg string, args ...any)
	Info(ctx context.Context, msg string, args ...any)
	Warn(ctx context.Context, msg string, args ...any)
	Error(ctx context.Context, msg string, args ...any)
}

var defaultLogger Logger = &slogLogger{
	logger: slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo})),
}

// SetLogger sets the default logger.
func SetLogger(l Logger) {
	if l != nil {
		defaultLogger = l
	}
}

// GetLogger returns the default logger.
func GetLogger() Logger {
	return defaultLogger
}

// Debug logs a debug message.
func Debug(ctx context.Context, msg string, args ...any) {
	defaultLogger.Debug(ctx, msg, args...)
}

// Info logs an info message.
func Info(ctx context.Context, msg string, args ...any) {
	defaultLogger.Info(ctx, msg, args...)
}

// Warn logs a warning message.
func Warn(ctx context.Context, msg string, args ...any) {
	defaultLogger.Warn(ctx, msg, args...)
}

// Error logs an error message.
func Error(ctx context.Context, msg string, args ...any) {
	defaultLogger.Error(ctx, msg, args...)
}

// slogLogger is the default logger using slog.
type slogLogger struct {
	logger *slog.Logger
}

// extractTraceArgs extracts trace info from context and prepends to args.
func extractTraceArgs(ctx context.Context, args []any) []any {
	if ctx == nil {
		return args
	}
	var traceArgs []any
	if traceId := ctx.Value(TraceKey{}); traceId != nil {
		traceArgs = append(traceArgs, "traceId", traceId)
	}
	if spanId := ctx.Value(SpanKey{}); spanId != nil {
		traceArgs = append(traceArgs, "spanId", spanId)
	}
	if len(traceArgs) > 0 {
		return append(traceArgs, args...)
	}
	return args
}

func (l *slogLogger) Debug(ctx context.Context, msg string, args ...any) {
	l.logger.DebugContext(ctx, msg, extractTraceArgs(ctx, args)...)
}

func (l *slogLogger) Info(ctx context.Context, msg string, args ...any) {
	l.logger.InfoContext(ctx, msg, extractTraceArgs(ctx, args)...)
}

func (l *slogLogger) Warn(ctx context.Context, msg string, args ...any) {
	l.logger.WarnContext(ctx, msg, extractTraceArgs(ctx, args)...)
}

func (l *slogLogger) Error(ctx context.Context, msg string, args ...any) {
	l.logger.ErrorContext(ctx, msg, extractTraceArgs(ctx, args)...)
}

// WithTraceId returns a new context with the given trace ID.
func WithTraceId(ctx context.Context, traceId string) context.Context {
	return context.WithValue(ctx, TraceKey{}, traceId)
}

// WithSpanId returns a new context with the given span ID.
func WithSpanId(ctx context.Context, spanId string) context.Context {
	return context.WithValue(ctx, SpanKey{}, spanId)
}

// GetTraceId returns the trace ID from context.
func GetTraceId(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	if v := ctx.Value(TraceKey{}); v != nil {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

// GetSpanId returns the span ID from context.
func GetSpanId(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	if v := ctx.Value(SpanKey{}); v != nil {
		if s, ok := v.(string); ok {
			return s
		}
	}
	return ""
}

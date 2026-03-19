package main

import (
	"compress/gzip"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"runtime/debug"
	"strconv"
	"strings"
)

// HandlerFunc is a handler that returns data and optional error
type HandlerFunc func(w http.ResponseWriter, r *http.Request) (interface{}, error)

// wrapHandler wraps a HandlerFunc into an http.HandlerFunc, handling response serialization
func wrapHandler(fn HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rv := recover(); rv != nil {
				if ece, ok := rv.(*ErrorCodeError); ok {
					writeJSON(w, r, &WebResult{Ret: ece.Code, Msg: ece.Msg})
					return
				}
				log.Printf("panic: %v\n%s", rv, debug.Stack())
				writeJSON(w, r, &WebResult{Ret: CodeInternalError, Msg: "内部错误"})
			}
		}()

		data, err := fn(w, r)
		if err != nil {
			if ece, ok := err.(*ErrorCodeError); ok {
				writeJSON(w, r, &WebResult{Ret: ece.Code, Msg: ece.Msg})
				return
			}
			log.Printf("handler error: %v", err)
			writeJSON(w, r, &WebResult{Ret: CodeInternalError, Msg: err.Error()})
			return
		}

		// If result is already a WebResult, write directly
		if wr, ok := data.(*WebResult); ok {
			writeJSON(w, r, wr)
			return
		}

		// Wrap in WebResult
		writeJSON(w, r, SuccessResult(data))
	}
}

func writeJSON(w http.ResponseWriter, r *http.Request, data interface{}) {
	jsonData, err := json.Marshal(data)
	if err != nil {
		http.Error(w, `{"ret":-1,"msg":"json marshal error"}`, http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json; charset=utf-8")

	// Gzip compression if client supports it and response is large enough
	if len(jsonData) > 1024 && strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		w.Header().Set("Content-Encoding", "gzip")
		gz := gzip.NewWriter(w)
		defer gz.Close()
		gz.Write(jsonData)
		return
	}

	w.Write(jsonData)
}

// corsMiddleware adds CORS headers
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		w.Header().Set("Access-Control-Max-Age", "86400")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// Helper to read JSON body
func readJSONBody(r *http.Request, v interface{}) error {
	defer r.Body.Close()
	body, err := io.ReadAll(r.Body)
	if err != nil {
		return err
	}
	return json.Unmarshal(body, v)
}

// Helper to get query params
func queryStr(r *http.Request, key string, defaultVal string) string {
	v := r.URL.Query().Get(key)
	if v == "" {
		return defaultVal
	}
	return v
}

func queryInt(r *http.Request, key string, defaultVal int) int {
	v := r.URL.Query().Get(key)
	if v == "" {
		return defaultVal
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return defaultVal
	}
	return n
}

func queryInt64(r *http.Request, key string) *int64 {
	v := r.URL.Query().Get(key)
	if v == "" {
		return nil
	}
	n := mustParseInt64(v)
	return &n
}

func queryInt64Required(r *http.Request, key string) (int64, error) {
	v := r.URL.Query().Get(key)
	if v == "" {
		return 0, InputError(key + " required")
	}
	return mustParseInt64(v), nil
}

func queryIntRequired(r *http.Request, key string) (int, error) {
	v := r.URL.Query().Get(key)
	if v == "" {
		return 0, InputError(key + " required")
	}
	return int(mustParseInt64(v)), nil
}

func queryIntPtr(r *http.Request, key string) *int {
	v := r.URL.Query().Get(key)
	if v == "" {
		return nil
	}
	n := int(mustParseInt64(v))
	return &n
}

func queryInt8(r *http.Request, key string) *int8 {
	v := r.URL.Query().Get(key)
	if v == "" {
		return nil
	}
	n := int8(mustParseInt64(v))
	return &n
}

func mustParseInt64(s string) int64 {
	n, err := strconv.ParseInt(s, 10, 64)
	if err != nil {
		panic(InputError("invalid number: " + s))
	}
	return n
}

func queryInt64List(r *http.Request, key string) []int64 {
	values := r.URL.Query()[key]
	if len(values) == 0 {
		// Try comma-separated in single value
		single := r.URL.Query().Get(key)
		if single == "" {
			return nil
		}
		values = strings.Split(single, ",")
	}
	var result []int64
	for _, v := range values {
		v = strings.TrimSpace(v)
		if v != "" {
			result = append(result, mustParseInt64(v))
		}
	}
	return result
}

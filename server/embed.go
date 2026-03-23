package main

import (
	"compress/gzip"
	"embed"
	"io"
	"io/fs"
	"net/http"
	"strings"
	"sync"
)

//go:embed web/*
var webFS embed.FS

// spaFileServer serves static files from the embedded filesystem
// with SPA fallback (returns index.html for non-API 404s)
type spaFileServer struct {
	handler http.Handler
	fsys    fs.FS
}

func NewSPAFileServer() http.Handler {
	subFS, err := fs.Sub(webFS, "web")
	if err != nil {
		// If web directory doesn't exist or is empty, return a noop handler
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, "frontend not embedded", http.StatusNotFound)
		})
	}

	fileServer := http.FileServer(http.FS(subFS))
	return &spaFileServer{
		handler: fileServer,
		fsys:    subFS,
	}
}

var gzipPool = sync.Pool{
	New: func() any {
		gz, _ := gzip.NewWriterLevel(io.Discard, gzip.BestSpeed)
		return gz
	},
}

type gzipResponseWriter struct {
	http.ResponseWriter
	gz *gzip.Writer
}

func (g *gzipResponseWriter) Write(b []byte) (int, error) {
	return g.gz.Write(b)
}

func shouldGzip(path string) bool {
	for _, ext := range []string{".js", ".css", ".html", ".svg", ".json"} {
		if strings.HasSuffix(path, ext) {
			return true
		}
	}
	return false
}

func (s *spaFileServer) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path

	// Skip API paths
	if strings.HasPrefix(path, "/ice-server/") {
		http.NotFound(w, r)
		return
	}

	// Remove leading slash for fs.Open
	fsPath := strings.TrimPrefix(path, "/")
	if fsPath == "" {
		fsPath = "index.html"
	}

	// Try to open the file
	f, err := s.fsys.Open(fsPath)
	if err != nil {
		// File not found: SPA fallback to index.html
		r.URL.Path = "/"
		fsPath = "index.html"
	} else {
		f.Close()
	}

	// Gzip compress text resources
	if shouldGzip(fsPath) && strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
		gz := gzipPool.Get().(*gzip.Writer)
		gz.Reset(w)
		defer func() {
			gz.Close()
			gzipPool.Put(gz)
		}()
		w.Header().Set("Content-Encoding", "gzip")
		w.Header().Set("Vary", "Accept-Encoding")
		w.Header().Del("Content-Length")
		s.handler.ServeHTTP(&gzipResponseWriter{ResponseWriter: w, gz: gz}, r)
		return
	}

	s.handler.ServeHTTP(w, r)
}

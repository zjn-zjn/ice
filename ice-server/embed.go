package main

import (
	"embed"
	"io/fs"
	"net/http"
	"strings"
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
		s.handler.ServeHTTP(w, r)
		return
	}
	f.Close()

	s.handler.ServeHTTP(w, r)
}

package main

import (
	"embed"
	"io/fs"
	"mime"
	"net/http"
	"path/filepath"
	"strings"
)

//go:embed web/*
var webFS embed.FS

// spaFileServer serves static files from the embedded filesystem
// with SPA fallback (returns index.html for non-API 404s)
// Pre-compressed .br files are served when available and client supports brotli
type spaFileServer struct {
	fsys       fs.FS
	fileServer http.Handler
}

func NewSPAFileServer() http.Handler {
	subFS, err := fs.Sub(webFS, "web")
	if err != nil {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, "frontend not embedded", http.StatusNotFound)
		})
	}
	return &spaFileServer{fsys: subFS, fileServer: http.FileServer(http.FS(subFS))}
}

func isCompressible(path string) bool {
	switch filepath.Ext(path) {
	case ".js", ".css", ".html", ".svg", ".json":
		return true
	}
	return false
}

func (s *spaFileServer) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path

	if strings.HasPrefix(path, "/ice-server/") {
		http.NotFound(w, r)
		return
	}

	fsPath := strings.TrimPrefix(path, "/")
	if fsPath == "" {
		fsPath = "index.html"
	}

	// Check if file exists, fallback to index.html for SPA
	if _, err := fs.Stat(s.fsys, fsPath); err != nil {
		fsPath = "index.html"
	}

	// Serve pre-compressed brotli if available and client supports it
	if isCompressible(fsPath) && strings.Contains(r.Header.Get("Accept-Encoding"), "br") {
		brPath := fsPath + ".br"
		if data, err := fs.ReadFile(s.fsys, brPath); err == nil {
			ct := mime.TypeByExtension(filepath.Ext(fsPath))
			if ct == "" {
				ct = "application/octet-stream"
			}
			w.Header().Set("Content-Type", ct)
			w.Header().Set("Content-Encoding", "br")
			w.Header().Set("Vary", "Accept-Encoding")
			if fsPath != "index.html" {
				// Hashed asset filenames are immutable
				w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
			}
			w.Write(data)
			return
		}
	}

	// Fallback: serve original file
	fallbackURL := *r.URL
	fallbackURL.Path = "/" + fsPath
	s.fileServer.ServeHTTP(w, &http.Request{
		Method:     r.Method,
		URL:        &fallbackURL,
		Header:     r.Header,
		Host:       r.Host,
		RequestURI: "/" + fsPath,
	})
}

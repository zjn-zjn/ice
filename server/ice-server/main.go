package main

import (
	"fmt"
	"log"
	"net/http"
)

func main() {
	// Load config
	config := LoadConfig()

	// Initialize storage
	storage, err := NewStorage(config.StoragePath)
	if err != nil {
		log.Fatalf("failed to initialize storage: %v", err)
	}

	// Initialize client manager
	clientManager := NewClientManager(config, storage)

	// Initialize services
	serverService := NewServerService(storage, clientManager, config)
	baseService := NewBaseService(storage, serverService)
	confService := NewConfService(storage, serverService, clientManager)
	appService := NewAppService(storage, clientManager)
	folderService := NewFolderService(storage, baseService)

	// Initialize handlers
	baseHandler := NewBaseHandler(baseService, serverService)
	confHandler := NewConfHandler(confService, serverService, appService, clientManager)
	appHandler := NewAppHandler(appService, serverService)
	folderHandler := NewFolderHandler(folderService, baseService)

	// Setup routes
	mux := http.NewServeMux()
	baseHandler.Register(mux)
	confHandler.Register(mux)
	appHandler.Register(mux)
	folderHandler.Register(mux)

	// SPA file server (catch-all for frontend)
	spaHandler := newSPAFileServer()
	mux.Handle("/", spaHandler)

	// Start scheduler
	scheduler := NewScheduler(config, serverService, clientManager)
	scheduler.Start()

	// Start server
	addr := fmt.Sprintf(":%d", config.Port)
	log.Printf("ice-server starting on %s (storage: %s)", addr, config.StoragePath)

	handler := corsMiddleware(mux)
	if err := http.ListenAndServe(addr, handler); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}

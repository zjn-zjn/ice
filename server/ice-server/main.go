package main

import (
	"fmt"
	"log"
	"net/http"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/handler"
	"github.com/waitmoon/ice-server/service"
	"github.com/waitmoon/ice-server/storage"
)

func main() {
	// Load config
	cfg := config.Load()

	// Initialize storage
	store, err := storage.NewStorage(cfg.StoragePath)
	if err != nil {
		log.Fatalf("failed to initialize storage: %v", err)
	}

	// Initialize client manager
	clientManager := service.NewClientManager(cfg, store)

	// Initialize services
	serverService := service.NewServerService(store, clientManager, cfg)
	baseService := service.NewBaseService(store, serverService)
	confService := service.NewConfService(store, serverService, clientManager)
	appService := service.NewAppService(store, clientManager)
	folderService := service.NewFolderService(store, baseService)

	// Initialize handlers
	baseHandler := handler.NewBaseHandler(baseService, serverService)
	confHandler := handler.NewConfHandler(confService, serverService, appService, clientManager)
	appHandler := handler.NewAppHandler(appService, serverService)
	folderHandler := handler.NewFolderHandler(folderService, baseService)

	// Setup routes
	mux := http.NewServeMux()
	baseHandler.Register(mux)
	confHandler.Register(mux)
	appHandler.Register(mux)
	folderHandler.Register(mux)

	// SPA file server (catch-all for frontend)
	spaHandler := NewSPAFileServer()
	mux.Handle("/", spaHandler)

	// Start scheduler
	scheduler := service.NewScheduler(cfg, serverService, clientManager)
	scheduler.Start()

	// Start server
	addr := fmt.Sprintf(":%d", cfg.Port)
	log.Printf("ice-server starting on %s (storage: %s)", addr, cfg.StoragePath)

	corsHandler := handler.CorsMiddleware(mux)
	if err := http.ListenAndServe(addr, corsHandler); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}

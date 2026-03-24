package main

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/handler"
	"github.com/waitmoon/ice-server/service"
	"github.com/waitmoon/ice-server/storage"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	})))

	// Load config
	cfg := config.Load()

	// Initialize storage
	store, err := storage.NewStorage(cfg.StoragePath)
	if err != nil {
		slog.Error("storage init failed", "error", err)
		os.Exit(1)
	}

	// Initialize client manager
	clientManager := service.NewClientManager(cfg, store)

	// Initialize services
	serverService := service.NewServerService(store, clientManager, cfg)
	baseService := service.NewBaseService(cfg, store, serverService)
	confService := service.NewConfService(cfg, store, serverService, clientManager)
	appService := service.NewAppService(store, clientManager)
	folderService := service.NewFolderService(store, baseService, serverService)

	// Initialize handlers
	baseHandler := handler.NewBaseHandler(baseService, serverService)
	confHandler := handler.NewConfHandler(confService, serverService, appService, clientManager)
	appHandler := handler.NewAppHandler(appService, serverService)
	folderHandler := handler.NewFolderHandler(folderService, baseService)

	// Initialize mock service and handler
	mockService := service.NewMockService(store, clientManager)
	mockHandler := handler.NewMockHandler(mockService, serverService)
	configHandler := handler.NewConfigHandler(cfg)
	publishHandler := handler.NewPublishHandler(cfg)

	// Setup routes
	mux := http.NewServeMux()
	baseHandler.Register(mux)
	confHandler.Register(mux)
	appHandler.Register(mux)
	folderHandler.Register(mux)
	mockHandler.Register(mux)
	configHandler.Register(mux)
	publishHandler.Register(mux)

	// SPA file server (catch-all for frontend)
	spaHandler := NewSPAFileServer()
	mux.Handle("/", spaHandler)

	// Start scheduler
	scheduler := service.NewScheduler(cfg, serverService, clientManager, store)
	scheduler.Start()

	// Start server with graceful shutdown
	addr := fmt.Sprintf(":%d", cfg.Port)
	slog.Info("server starting", "addr", addr, "storagePath", cfg.StoragePath)

	corsHandler := handler.CorsMiddleware(mux)
	srv := &http.Server{
		Addr:    addr,
		Handler: corsHandler,
	}

	go func() {
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			slog.Error("server failed", "error", err)
			os.Exit(1)
		}
	}()

	// Wait for interrupt signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	slog.Info("server shutting down")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("server shutdown failed", "error", err)
	}
	slog.Info("server stopped")
}

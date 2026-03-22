// Package main is the entry point for the ice-test Go application.
package main

import (
	"encoding/json"
	"flag"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"

	ice "github.com/zjn-zjn/ice/sdks/go"
	"github.com/zjn-zjn/ice/tests/go/ice-test/config"

	// Import leaf packages to trigger init() registration
	_ "github.com/zjn-zjn/ice/tests/go/ice-test/flow"
	_ "github.com/zjn-zjn/ice/tests/go/ice-test/none"
	_ "github.com/zjn-zjn/ice/tests/go/ice-test/result"
)

var (
	configPath  = flag.String("config", "config.yml", "Config file path")
	port        = flag.Int("port", 0, "HTTP server port (overrides config)")
	storagePath = flag.String("storage", "", "Ice data storage path (overrides config)")
	app         = flag.Int("app", 0, "Application ID (overrides config)")
)

func main() {
	flag.Parse()

	// Initialize logging
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelDebug})))

	// Load config
	cfg, err := config.Load(*configPath)
	if err != nil {
		slog.Warn("failed to load config file, using defaults", "path", *configPath, "error", err)
		cfg = config.DefaultConfig()
	} else {
		slog.Info("loaded config", "path", *configPath)
	}

	// Command line overrides
	if *port > 0 {
		cfg.Server.Port = *port
	}
	if *storagePath != "" {
		cfg.Ice.Storage.Path = *storagePath
	}
	if *app > 0 {
		cfg.Ice.App = *app
	}

	// Create and start file client
	client, err := ice.NewClientWithOptions(
		cfg.Ice.App,
		cfg.Ice.Storage.Path,
		cfg.Ice.Pool.Parallelism,
		cfg.GetPollInterval(),
		cfg.GetHeartbeatInterval(),
		"",
	)
	if err != nil {
		slog.Error("failed to create file client", "error", err)
		os.Exit(1)
	}

	if err := client.Start(); err != nil {
		slog.Error("failed to start file client", "error", err)
		os.Exit(1)
	}
	defer client.Destroy()

	// Setup HTTP handlers
	http.HandleFunc("/test", handleTest)
	http.HandleFunc("/recharge", handleRecharge)
	http.HandleFunc("/consume", handleConsume)
	http.HandleFunc("/health", handleHealth)

	// Start HTTP server
	addr := ":" + strconv.Itoa(cfg.Server.Port)
	slog.Info("starting ice-test server", "port", cfg.Server.Port, "storage", cfg.Ice.Storage.Path, "app", cfg.Ice.App)

	server := &http.Server{Addr: addr}

	// Graceful shutdown
	go func() {
		sigCh := make(chan os.Signal, 1)
		signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
		<-sigCh
		slog.Info("shutting down server...")
		server.Close()
	}()

	if err := server.ListenAndServe(); err != http.ErrServerClosed {
		slog.Error("server error", "error", err)
	}
}

// handleTest handles POST /test with a JSON body
func handleTest(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var reqData map[string]any
	if err := json.NewDecoder(r.Body).Decode(&reqData); err != nil {
		http.Error(w, "invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	roam := ice.NewRoamWithMeta()
	meta := roam.GetMeta()

	// Parse meta fields
	if iceId, ok := reqData["iceId"].(float64); ok {
		meta.Id = int64(iceId)
	}
	if scene, ok := reqData["scene"].(string); ok {
		meta.Scene = scene
	}
	if confId, ok := reqData["confId"].(float64); ok {
		meta.Nid = int64(confId)
	}
	if debug, ok := reqData["debug"].(float64); ok {
		meta.Debug = byte(debug)
	}
	if roamData, ok := reqData["roam"].(map[string]any); ok {
		for k, v := range roamData {
			roam.Put(k, v)
		}
	}

	// Create context with trace ID
	ctx := ice.WithTraceId(r.Context(), meta.Trace)

	// Process
	roamList := ice.SyncProcess(ctx, roam)

	// Return result
	result := make([]map[string]any, 0, len(roamList))
	for _, r := range roamList {
		item := map[string]any{
			"iceId": r.GetIceId(),
			"roam":  r.Data(),
		}
		if r.GetIceProcess() != nil {
			item["processInfo"] = r.GetIceProcess().String()
		}
		result = append(result, item)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// handleRecharge handles GET /recharge?cost=xxx&uid=xxx
func handleRecharge(w http.ResponseWriter, r *http.Request) {
	cost, _ := strconv.Atoi(r.URL.Query().Get("cost"))
	uid, _ := strconv.Atoi(r.URL.Query().Get("uid"))

	roam := ice.NewRoamWithMeta()
	roam.GetMeta().Scene = "recharge"
	roam.Put("cost", cost)
	roam.Put("uid", uid)

	ctx := ice.WithTraceId(r.Context(), roam.GetIceTrace())
	ice.SyncProcess(ctx, roam)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(roam.Data())
}

// handleConsume handles GET /consume?cost=xxx&uid=xxx
func handleConsume(w http.ResponseWriter, r *http.Request) {
	cost, _ := strconv.Atoi(r.URL.Query().Get("cost"))
	uid, _ := strconv.Atoi(r.URL.Query().Get("uid"))

	roam := ice.NewRoamWithMeta()
	roam.GetMeta().Scene = "consume"
	roam.Put("cost", cost)
	roam.Put("uid", uid)

	ctx := ice.WithTraceId(r.Context(), roam.GetIceTrace())
	ice.SyncProcess(ctx, roam)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(roam.Data())
}

// handleHealth handles GET /health
func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

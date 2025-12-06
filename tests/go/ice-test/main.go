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
	"time"

	ice "github.com/waitmoon/ice/sdks/go"
)

var (
	port        = flag.Int("port", 8084, "HTTP server port")
	storagePath = flag.String("storage", "../../../ice-data", "Ice data storage path")
	app         = flag.Int("app", 1, "Application ID")
)

func main() {
	flag.Parse()

	// Initialize logging
	slog.SetDefault(slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelDebug})))

	// Register leaf nodes
	registerLeafNodes()

	// Create and start file client
	client, err := ice.NewClientWithOptions(*app, *storagePath, -1, 5*time.Second, 10*time.Second)
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
	addr := ":" + strconv.Itoa(*port)
	slog.Info("starting ice-test server", "port", *port, "storage", *storagePath, "app", *app)

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

	var packData map[string]any
	if err := json.NewDecoder(r.Body).Decode(&packData); err != nil {
		http.Error(w, "invalid JSON: "+err.Error(), http.StatusBadRequest)
		return
	}

	pack := ice.NewPack()

	// Parse pack fields
	if iceId, ok := packData["iceId"].(float64); ok {
		pack.SetIceId(int64(iceId))
	}
	if scene, ok := packData["scene"].(string); ok {
		pack.SetScene(scene)
	}
	if confId, ok := packData["confId"].(float64); ok {
		pack.SetConfId(int64(confId))
	}
	if debug, ok := packData["debug"].(float64); ok {
		pack.Debug = byte(debug)
	}
	if roamData, ok := packData["roam"].(map[string]any); ok {
		for k, v := range roamData {
			pack.Roam.Put(k, v)
		}
	}

	// Create context with trace ID
	ctx := ice.WithTraceId(r.Context(), pack.TraceId)

	// Process
	ctxList := ice.SyncProcess(ctx, pack)

	// Return result
	result := make([]map[string]any, 0, len(ctxList))
	for _, iceCtx := range ctxList {
		item := map[string]any{
			"iceId": iceCtx.IceId,
		}
		if iceCtx.Pack != nil {
			item["roam"] = iceCtx.Pack.Roam.Data()
		}
		if iceCtx.ProcessInfo != nil {
			item["processInfo"] = iceCtx.ProcessInfo.String()
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

	pack := ice.NewPack().SetScene("recharge")
	pack.Roam.Put("cost", cost)
	pack.Roam.Put("uid", uid)

	ctx := ice.WithTraceId(r.Context(), pack.TraceId)
	ice.SyncProcess(ctx, pack)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(pack)
}

// handleConsume handles GET /consume?cost=xxx&uid=xxx
func handleConsume(w http.ResponseWriter, r *http.Request) {
	cost, _ := strconv.Atoi(r.URL.Query().Get("cost"))
	uid, _ := strconv.Atoi(r.URL.Query().Get("uid"))

	pack := ice.NewPack().SetScene("consume")
	pack.Roam.Put("cost", cost)
	pack.Roam.Put("uid", uid)

	ctx := ice.WithTraceId(r.Context(), pack.TraceId)
	ice.SyncProcess(ctx, pack)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(pack)
}

// handleHealth handles GET /health
func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

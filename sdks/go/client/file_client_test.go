package client

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/waitmoon/ice/sdks/go/dto"
	"github.com/waitmoon/ice/sdks/go/enum"
)

const testApp = 1

func setupTestStorage(t *testing.T) string {
	// Create temp directory
	tempDir, err := os.MkdirTemp("", "ice-file-client-test")
	if err != nil {
		t.Fatalf("failed to create temp dir: %v", err)
	}

	appDir := filepath.Join(tempDir, "1")

	// Create directory structure
	dirs := []string{
		filepath.Join(appDir, "bases"),
		filepath.Join(appDir, "confs"),
		filepath.Join(appDir, "versions"),
		filepath.Join(tempDir, "clients", "1"),
	}
	for _, dir := range dirs {
		if err := os.MkdirAll(dir, 0755); err != nil {
			t.Fatalf("failed to create dir %s: %v", dir, err)
		}
	}

	// Create version file
	if err := os.WriteFile(filepath.Join(appDir, "version.txt"), []byte("1"), 0644); err != nil {
		t.Fatalf("failed to write version file: %v", err)
	}

	// Create base
	base := dto.BaseDto{
		Id:       1,
		ConfId:   1,
		Scenes:   "scene1",
		TimeType: byte(enum.TimeNone),
		Debug:    1,
		Priority: 1,
	}
	baseData, _ := json.Marshal(base)
	if err := os.WriteFile(filepath.Join(appDir, "bases", "1.json"), baseData, 0644); err != nil {
		t.Fatalf("failed to write base file: %v", err)
	}

	// Create conf
	conf := dto.ConfDto{
		Id:       1,
		Name:     "test-root",
		Type:     byte(enum.TypeTrue),
		TimeType: byte(enum.TimeNone),
		Debug:    1,
		Inverse:  false,
	}
	confData, _ := json.Marshal(conf)
	if err := os.WriteFile(filepath.Join(appDir, "confs", "1.json"), confData, 0644); err != nil {
		t.Fatalf("failed to write conf file: %v", err)
	}

	return tempDir
}

func cleanupTestStorage(path string) {
	os.RemoveAll(path)
}

func TestFileClient_Creation(t *testing.T) {
	tempDir := setupTestStorage(t)
	defer cleanupTestStorage(tempDir)

	client, err := New(testApp, tempDir)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	if client.app != testApp {
		t.Errorf("expected app=%d, got %d", testApp, client.app)
	}
	if client.storagePath != tempDir {
		t.Errorf("expected storagePath=%s, got %s", tempDir, client.storagePath)
	}
}

func TestFileClient_StartAndDestroy(t *testing.T) {
	tempDir := setupTestStorage(t)
	defer cleanupTestStorage(tempDir)

	client, err := New(testApp, tempDir)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	// Start client
	if err := client.Start(); err != nil {
		t.Fatalf("failed to start client: %v", err)
	}

	// Verify loaded version
	if client.GetLoadedVersion() < 0 {
		t.Error("loadedVersion should be >= 0 after start")
	}

	// Verify client file exists
	clientFiles, _ := os.ReadDir(filepath.Join(tempDir, "clients", "1"))
	if len(clientFiles) == 0 {
		t.Error("client info file should exist")
	}

	// Destroy client
	client.Destroy()

	// Verify destroyed
	if !client.destroy.Load() {
		t.Error("client should be marked as destroyed")
	}
}

func TestFileClient_ConfigLoading(t *testing.T) {
	tempDir := setupTestStorage(t)
	defer cleanupTestStorage(tempDir)

	client, err := New(testApp, tempDir)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	if err := client.Start(); err != nil {
		t.Fatalf("failed to start client: %v", err)
	}
	defer client.Destroy()

	// Verify version loaded
	if client.GetLoadedVersion() != 1 {
		t.Errorf("expected loadedVersion=1, got %d", client.GetLoadedVersion())
	}
}

func TestFileClient_IncrementalUpdate(t *testing.T) {
	tempDir := setupTestStorage(t)
	defer cleanupTestStorage(tempDir)

	client, err := NewWithOptions(testApp, tempDir, -1, 1*time.Second, 10*time.Second)
	if err != nil {
		t.Fatalf("failed to create client: %v", err)
	}

	if err := client.Start(); err != nil {
		t.Fatalf("failed to start client: %v", err)
	}
	defer client.Destroy()

	// Verify initial version
	if client.GetLoadedVersion() != 1 {
		t.Errorf("expected loadedVersion=1, got %d", client.GetLoadedVersion())
	}

	appDir := filepath.Join(tempDir, "1")

	// Create new conf
	newConf := dto.ConfDto{
		Id:       2,
		Name:     "new-conf",
		Type:     byte(enum.TypeTrue),
		TimeType: byte(enum.TimeNone),
		Debug:    1,
	}
	confData, _ := json.Marshal(newConf)
	os.WriteFile(filepath.Join(appDir, "confs", "2.json"), confData, 0644)

	// Create version update file
	update := dto.TransferDto{
		Version:             2,
		InsertOrUpdateConfs: []dto.ConfDto{newConf},
	}
	updateData, _ := json.Marshal(update)
	os.WriteFile(filepath.Join(appDir, "versions", "2_upd.json"), updateData, 0644)

	// Update version
	os.WriteFile(filepath.Join(appDir, "version.txt"), []byte("2"), 0644)

	// Wait for poll to detect update
	time.Sleep(2 * time.Second)

	// Verify version updated
	if client.GetLoadedVersion() != 2 {
		t.Errorf("expected loadedVersion=2, got %d", client.GetLoadedVersion())
	}
}

func TestFileClient_InvalidPath(t *testing.T) {
	client, err := New(testApp, "/nonexistent/path")
	if err != nil {
		t.Fatalf("should not error on creation: %v", err)
	}

	// Start should fail
	if err := client.Start(); err == nil {
		t.Error("expected error when starting with invalid path")
	}
}

func TestFileClient_DoubleStart(t *testing.T) {
	tempDir := setupTestStorage(t)
	defer cleanupTestStorage(tempDir)

	client, _ := New(testApp, tempDir)
	client.Start()
	defer client.Destroy()

	// Second start should be no-op
	if err := client.Start(); err != nil {
		t.Error("double start should not error")
	}
}

func TestFileClient_DoubleDestroy(t *testing.T) {
	tempDir := setupTestStorage(t)
	defer cleanupTestStorage(tempDir)

	client, _ := New(testApp, tempDir)
	client.Start()

	// First destroy
	client.Destroy()

	// Second destroy should be no-op (no panic)
	client.Destroy()
}

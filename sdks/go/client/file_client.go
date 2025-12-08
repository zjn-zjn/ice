// Package client provides the ice file client for configuration loading.
package client

import (
	"context"
	"encoding/json"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/waitmoon/ice/sdks/go/dto"
	"github.com/waitmoon/ice/sdks/go/internal/executor"
	"github.com/waitmoon/ice/sdks/go/internal/uuid"
	"github.com/waitmoon/ice/sdks/go/leaf"
	"github.com/waitmoon/ice/sdks/go/log"
)

const (
	dirBases    = "bases"
	dirConfs    = "confs"
	dirVersions = "versions"
	dirClients  = "clients"

	fileVersion = "version.txt"
	suffixJSON  = ".json"
	suffixUpd   = "_upd.json"
	suffixTmp   = ".tmp"

	statusDeleted byte = 255 // -1 as byte

	defaultPollInterval      = 5 * time.Second
	defaultHeartbeatInterval = 30 * time.Second
)

// FileClient is a file-based ice client.
type FileClient struct {
	app               int
	storagePath       string
	parallelism       int
	pollInterval      time.Duration
	heartbeatInterval time.Duration
	address           string
	leafNodes         []dto.LeafNodeInfo
	loadedVersion     atomic.Int64
	started           atomic.Bool
	destroy           atomic.Bool
	stopCh            chan struct{}
	wg                sync.WaitGroup
}

// New creates a new FileClient with minimal configuration.
// This is the simplest and recommended way to create a client.
func New(app int, storagePath string) (*FileClient, error) {
	return NewWithOptions(app, storagePath, -1, defaultPollInterval, defaultHeartbeatInterval)
}

// NewWithOptions creates a new FileClient with custom options.
func NewWithOptions(app int, storagePath string, parallelism int, pollInterval, heartbeatInterval time.Duration) (*FileClient, error) {
	if pollInterval <= 0 {
		pollInterval = defaultPollInterval
	}
	if heartbeatInterval <= 0 {
		heartbeatInterval = defaultHeartbeatInterval
	}

	client := &FileClient{
		app:               app,
		storagePath:       storagePath,
		parallelism:       parallelism,
		pollInterval:      pollInterval,
		heartbeatInterval: heartbeatInterval,
		address:           getAddress(app),
		stopCh:            make(chan struct{}),
	}

	// Get registered leaf nodes
	client.leafNodes = leaf.GetLeafNodes()

	// Initialize executor
	if err := executor.Init(parallelism); err != nil {
		return nil, err
	}

	return client, nil
}

// Start starts the client.
func (c *FileClient) Start() error {
	if c.destroy.Load() {
		return nil
	}

	ctx := context.Background()
	startTime := time.Now()

	// Ensure directories exist
	if err := c.ensureDirectories(); err != nil {
		return err
	}

	// Load initial config
	if err := c.loadInitialConfig(ctx); err != nil {
		return err
	}

	// Register client
	if err := c.registerClient(); err != nil {
		log.Warn(ctx, "failed to register client", "error", err)
	}

	// Start version poller
	c.wg.Add(1)
	go c.versionPoller()

	// Start heartbeat
	c.wg.Add(1)
	go c.heartbeat()

	c.started.Store(true)
	log.Info(ctx, "ice file client started", "app", c.app, "address", c.address,
		"durationMs", time.Since(startTime).Milliseconds(), "storagePath", c.storagePath)

	return nil
}

// Destroy stops the client.
func (c *FileClient) Destroy() {
	// Use CompareAndSwap to ensure we only destroy once
	if !c.destroy.CompareAndSwap(false, true) {
		return // Already destroyed
	}
	c.started.Store(false)
	close(c.stopCh)
	c.wg.Wait()

	// Unregister client
	c.unregisterClient()
	executor.Shutdown()
	log.Info(context.Background(), "ice file client destroyed", "app", c.app, "address", c.address)
}

// IsStarted returns true if the client is started.
func (c *FileClient) IsStarted() bool {
	return c.started.Load()
}

// WaitStarted blocks until the client is started.
func (c *FileClient) WaitStarted() {
	for !c.started.Load() && !c.destroy.Load() {
		time.Sleep(100 * time.Millisecond)
	}
}

func (c *FileClient) ensureDirectories() error {
	appPath := filepath.Join(c.storagePath, strconv.Itoa(c.app))

	dirs := []string{
		filepath.Join(appPath, dirBases),
		filepath.Join(appPath, dirConfs),
		filepath.Join(appPath, dirVersions),
		filepath.Join(c.storagePath, dirClients, strconv.Itoa(c.app)),
	}

	for _, dir := range dirs {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	}
	return nil
}

func (c *FileClient) loadInitialConfig(ctx context.Context) error {
	initData, err := c.loadAllConfig()
	if err != nil {
		return err
	}

	if initData != nil {
		errors := Update(initData)
		if len(errors) > 0 {
			log.Warn(ctx, "ice init config has errors", "errors", errors)
		}
		c.loadedVersion.Store(initData.Version)
	}

	log.Info(ctx, "ice file client loaded initial config", "version", c.loadedVersion.Load())
	return nil
}

func (c *FileClient) loadAllConfig() (*dto.TransferDto, error) {
	appPath := filepath.Join(c.storagePath, strconv.Itoa(c.app))

	d := &dto.TransferDto{}

	// Read version
	versionPath := filepath.Join(appPath, fileVersion)
	if data, err := os.ReadFile(versionPath); err == nil {
		versionStr := strings.TrimSpace(string(data))
		if v, err := strconv.ParseInt(versionStr, 10, 64); err == nil {
			d.Version = v
		}
	}

	// Read bases
	basesPath := filepath.Join(appPath, dirBases)
	if entries, err := os.ReadDir(basesPath); err == nil {
		for _, entry := range entries {
			if !entry.IsDir() && filepath.Ext(entry.Name()) == suffixJSON {
				filePath := filepath.Join(basesPath, entry.Name())
				if data, err := os.ReadFile(filePath); err == nil {
					var base dto.BaseDto
					if err := json.Unmarshal(data, &base); err == nil {
						if base.Status != statusDeleted {
							d.InsertOrUpdateBases = append(d.InsertOrUpdateBases, base)
						}
					}
				}
			}
		}
	}

	// Read confs
	confsPath := filepath.Join(appPath, dirConfs)
	if entries, err := os.ReadDir(confsPath); err == nil {
		for _, entry := range entries {
			if !entry.IsDir() && filepath.Ext(entry.Name()) == suffixJSON {
				filePath := filepath.Join(confsPath, entry.Name())
				if data, err := os.ReadFile(filePath); err == nil {
					var conf dto.ConfDto
					if err := json.Unmarshal(data, &conf); err == nil {
						if conf.Status != statusDeleted {
							d.InsertOrUpdateConfs = append(d.InsertOrUpdateConfs, conf)
						}
					}
				}
			}
		}
	}

	return d, nil
}

func (c *FileClient) versionPoller() {
	defer c.wg.Done()
	ctx := context.Background()
	ticker := time.NewTicker(c.pollInterval)
	defer ticker.Stop()

	for {
		select {
		case <-c.stopCh:
			return
		case <-ticker.C:
			if err := c.checkAndUpdateVersion(ctx); err != nil {
				log.Error(ctx, "version poll error", "error", err)
			}
		}
	}
}

func (c *FileClient) checkAndUpdateVersion(ctx context.Context) error {
	versionPath := filepath.Join(c.storagePath, strconv.Itoa(c.app), fileVersion)
	data, err := os.ReadFile(versionPath)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}

	versionStr := strings.TrimSpace(string(data))
	currentVersion, err := strconv.ParseInt(versionStr, 10, 64)
	if err != nil {
		return err
	}

	if currentVersion > c.loadedVersion.Load() {
		log.Info(ctx, "detected version change", "from", c.loadedVersion.Load(), "to", currentVersion)
		return c.loadIncrementalUpdates(ctx, currentVersion)
	}
	return nil
}

func (c *FileClient) loadIncrementalUpdates(ctx context.Context, targetVersion int64) error {
	versionsPath := filepath.Join(c.storagePath, strconv.Itoa(c.app), dirVersions)
	needFullLoad := false

	for v := c.loadedVersion.Load() + 1; v <= targetVersion; v++ {
		updatePath := filepath.Join(versionsPath, strconv.FormatInt(v, 10)+suffixUpd)
		data, err := os.ReadFile(updatePath)
		if err != nil {
			if os.IsNotExist(err) {
				if v == targetVersion {
					// Only the last version file is missing - normal case, wait for next poll
					log.Info(ctx, "latest update file not ready, will retry", "version", v)
				} else {
					// Middle version file is missing - abnormal, need full load
					log.Warn(ctx, "middle update file missing, will do full load", "version", v)
					needFullLoad = true
				}
				break
			}
			return err
		}

		var updateDto dto.TransferDto
		if err := json.Unmarshal(data, &updateDto); err != nil {
			log.Error(ctx, "failed to parse incremental update", "version", v, "error", err)
			needFullLoad = true
			break
		}

		errors := Update(&updateDto)
		if len(errors) > 0 {
			log.Warn(ctx, "incremental update has errors", "version", v, "errors", errors)
		}
		c.loadedVersion.Store(v)
		log.Info(ctx, "loaded incremental update", "version", v)
	}

	if needFullLoad {
		log.Info(ctx, "performing full config reload")
		fullDto, err := c.loadAllConfig()
		if err != nil {
			return err
		}
		if fullDto != nil {
			errors := Update(fullDto)
			if len(errors) > 0 {
				log.Warn(ctx, "full reload has errors", "errors", errors)
			}
			c.loadedVersion.Store(fullDto.Version)
			log.Info(ctx, "full config reload completed", "version", c.loadedVersion.Load())
		}
	}

	// Update client version info
	c.updateClientVersion()
	return nil
}

func (c *FileClient) heartbeat() {
	defer c.wg.Done()
	ticker := time.NewTicker(c.heartbeatInterval)
	defer ticker.Stop()

	for {
		select {
		case <-c.stopCh:
			return
		case <-ticker.C:
			c.updateHeartbeat()
		}
	}
}

func (c *FileClient) registerClient() error {
	clientInfo := &dto.ClientInfo{
		Address:       c.address,
		App:           c.app,
		LeafNodes:     c.leafNodes,
		LastHeartbeat: time.Now().UnixMilli(),
		StartTime:     time.Now().UnixMilli(),
		LoadedVersion: c.loadedVersion.Load(),
	}
	if err := c.writeClientInfo(clientInfo); err != nil {
		return err
	}
	// 每次注册都覆盖 _latest.json，server 从这里获取最新的叶子节点结构
	if len(c.leafNodes) > 0 {
		_ = c.writeLatestInfo(clientInfo)
	}
	return nil
}

func (c *FileClient) writeLatestInfo(clientInfo *dto.ClientInfo) error {
	latestPath := filepath.Join(c.storagePath, dirClients, strconv.Itoa(c.app), "_latest.json")
	data, err := json.Marshal(clientInfo)
	if err != nil {
		return err
	}
	tmpPath := latestPath + suffixTmp
	if err := os.WriteFile(tmpPath, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmpPath, latestPath)
}

func (c *FileClient) unregisterClient() {
	clientPath := c.getClientFilePath()
	_ = os.Remove(clientPath)
	log.Info(context.Background(), "ice client unregistered", "address", c.address)
}

func (c *FileClient) writeClientInfo(clientInfo *dto.ClientInfo) error {
	clientPath := c.getClientFilePath()
	dir := filepath.Dir(clientPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	data, err := json.Marshal(clientInfo)
	if err != nil {
		return err
	}

	tmpPath := clientPath + suffixTmp
	if err := os.WriteFile(tmpPath, data, 0644); err != nil {
		return err
	}

	return os.Rename(tmpPath, clientPath)
}

func (c *FileClient) getClientFilePath() string {
	// Replace special characters in address
	safeAddress := strings.ReplaceAll(c.address, ":", "_")
	safeAddress = strings.ReplaceAll(safeAddress, "/", "_")
	return filepath.Join(c.storagePath, dirClients, strconv.Itoa(c.app), safeAddress+suffixJSON)
}

func (c *FileClient) updateClientVersion() {
	clientPath := c.getClientFilePath()
	data, err := os.ReadFile(clientPath)
	if err != nil {
		return
	}

	var clientInfo dto.ClientInfo
	if err := json.Unmarshal(data, &clientInfo); err != nil {
		return
	}

	clientInfo.LoadedVersion = c.loadedVersion.Load()
	clientInfo.LastHeartbeat = time.Now().UnixMilli()
	_ = c.writeClientInfo(&clientInfo)
}

func (c *FileClient) updateHeartbeat() {
	clientPath := c.getClientFilePath()
	data, err := os.ReadFile(clientPath)
	if err != nil {
		if os.IsNotExist(err) {
			_ = c.registerClient()
		}
		return
	}

	var clientInfo dto.ClientInfo
	if err := json.Unmarshal(data, &clientInfo); err != nil {
		return
	}

	clientInfo.LastHeartbeat = time.Now().UnixMilli()
	_ = c.writeClientInfo(&clientInfo)
}

func getAddress(app int) string {
	hostname, _ := os.Hostname()
	if hostname == "" {
		hostname = "unknown"
	}
	return hostname + "/" + strconv.Itoa(app) + "/" + uuid.GenerateShortId()
}

// GetApp returns the app ID.
func (c *FileClient) GetApp() int {
	return c.app
}

// GetStoragePath returns the storage path.
func (c *FileClient) GetStoragePath() string {
	return c.storagePath
}

// GetLoadedVersion returns the loaded version.
func (c *FileClient) GetLoadedVersion() int64 {
	return c.loadedVersion.Load()
}

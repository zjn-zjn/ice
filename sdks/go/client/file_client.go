// Package client provides the ice file client for configuration loading.
package client

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/zjn-zjn/ice/sdks/go/cache"
	icecontext "github.com/zjn-zjn/ice/sdks/go/context"
	"github.com/zjn-zjn/ice/sdks/go/dto"
	"github.com/zjn-zjn/ice/sdks/go/handler"
	"github.com/zjn-zjn/ice/sdks/go/internal/executor"
	"github.com/zjn-zjn/ice/sdks/go/internal/uuid"
	"github.com/zjn-zjn/ice/sdks/go/leaf"
	"github.com/zjn-zjn/ice/sdks/go/log"
	icenode "github.com/zjn-zjn/ice/sdks/go/node"
)

const (
	dirBases    = "bases"
	dirConfs    = "confs"
	dirVersions = "versions"
	dirClients  = "clients"
	dirLane     = "lane"
	dirMock     = "mock"

	fileVersion = "version.txt"
	suffixJSON  = ".json"
	suffixUpd   = "_upd.json"
	suffixTmp = ".tmp"

	prefixMeta = "m_"
	prefixBeat = "b_"

	statusDeleted byte = 255 // -1 as byte

	defaultPollInterval      = 2 * time.Second
	defaultHeartbeatInterval = 10 * time.Second
)

// FileClient is a file-based ice client.
type FileClient struct {
	app               int
	storagePath       string
	parallelism       int
	pollInterval      time.Duration
	heartbeatInterval time.Duration
	address           string
	lane              string
	leafNodes         []dto.LeafNodeInfo
	loadedVersion     atomic.Int64
	startTime         int64
	started           atomic.Bool
	destroy           atomic.Bool
	stopCh            chan struct{}
	wg                sync.WaitGroup
}

// New creates a new FileClient. Optional lane parameter for swimlane support.
func New(app int, storagePath string, lane ...string) (*FileClient, error) {
	l := ""
	if len(lane) > 0 {
		l = lane[0]
	}
	return NewWithOptions(app, storagePath, -1, defaultPollInterval, defaultHeartbeatInterval, l)
}

// NewWithOptions creates a new FileClient with custom options.
func NewWithOptions(app int, storagePath string, parallelism int, pollInterval, heartbeatInterval time.Duration, lane string) (*FileClient, error) {
	if pollInterval <= 0 {
		pollInterval = defaultPollInterval
	}
	if heartbeatInterval <= 0 {
		heartbeatInterval = defaultHeartbeatInterval
	}

	trimmedLane := strings.TrimSpace(lane)

	client := &FileClient{
		app:               app,
		storagePath:       storagePath,
		parallelism:       parallelism,
		pollInterval:      pollInterval,
		heartbeatInterval: heartbeatInterval,
		address:           getAddress(app),
		lane:              trimmedLane,
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
	c.startTime = time.Now().UnixMilli()
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
		log.Warn(ctx, "client register failed", "error", err)
	}

	// Start version poller (heartbeat is merged into poller via counter)
	c.wg.Add(1)
	go c.versionPoller()

	c.started.Store(true)
	log.Info(ctx, "client started", "app", c.app, "address", c.address,
		"lane", c.lane, "durationMs", time.Since(startTime).Milliseconds(), "storagePath", c.storagePath)

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
	log.Info(context.Background(), "client stopped", "app", c.app, "address", c.address)
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
		c.getClientsDir(),
	}

	for _, dir := range dirs {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	}
	return nil
}

func (c *FileClient) getClientsDir() string {
	if c.lane != "" {
		return filepath.Join(c.storagePath, dirClients, strconv.Itoa(c.app), dirLane, c.lane)
	}
	return filepath.Join(c.storagePath, dirClients, strconv.Itoa(c.app))
}

func (c *FileClient) loadInitialConfig(ctx context.Context) error {
	initData, err := c.loadAllConfig()
	if err != nil {
		return err
	}

	if initData != nil {
		errors := Update(initData)
		if len(errors) > 0 {
			log.Warn(ctx, "initial config loaded with errors", "errors", errors)
		}
		c.loadedVersion.Store(initData.Version)
	}

	log.Info(ctx, "initial config loaded", "version", c.loadedVersion.Load())
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

	// Read bases (recursively walk directories to support folder structure)
	basesPath := filepath.Join(appPath, dirBases)
	filepath.WalkDir(basesPath, func(path string, entry os.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return nil
		}
		if filepath.Ext(entry.Name()) == suffixJSON {
			if data, readErr := os.ReadFile(path); readErr == nil {
				var base dto.BaseDto
				if json.Unmarshal(data, &base) == nil && base.Status != statusDeleted {
					d.InsertOrUpdateBases = append(d.InsertOrUpdateBases, base)
				}
			}
		}
		return nil
	})

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

	heartbeatTicks := int(c.heartbeatInterval / c.pollInterval)
	if heartbeatTicks < 1 {
		heartbeatTicks = 1
	}
	tickCount := 0

	for {
		select {
		case <-c.stopCh:
			return
		case <-ticker.C:
			if err := c.checkAndUpdateVersion(ctx); err != nil {
				log.Error(ctx, "version poll failed", "error", err)
			}
			c.checkMocks(ctx)
			tickCount++
			if tickCount >= heartbeatTicks {
				tickCount = 0
				c.updateHeartbeat()
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
		log.Info(ctx, "version changed", "from", c.loadedVersion.Load(), "to", currentVersion)
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
					log.Info(ctx, "update file not ready, retrying", "version", v)
				} else {
					// Middle version file is missing - abnormal, need full load
					log.Warn(ctx, "incremental file missing, falling back to full load", "version", v)
					needFullLoad = true
				}
				break
			}
			return err
		}

		var updateDto dto.TransferDto
		if err := json.Unmarshal(data, &updateDto); err != nil {
			log.Error(ctx, "incremental update parse failed", "version", v, "error", err)
			needFullLoad = true
			break
		}

		errors := Update(&updateDto)
		if len(errors) > 0 {
			log.Warn(ctx, "incremental update loaded with errors", "version", v, "errors", errors)
		}
		c.loadedVersion.Store(v)
		log.Info(ctx, "incremental update loaded", "version", v)
	}

	if needFullLoad {
		log.Info(ctx, "full reload started")
		fullDto, err := c.loadAllConfig()
		if err != nil {
			return err
		}
		if fullDto != nil {
			errors := Update(fullDto)
			if len(errors) > 0 {
				log.Warn(ctx, "full reload completed with errors", "errors", errors)
			}
			c.loadedVersion.Store(fullDto.Version)
			log.Info(ctx, "full reload completed", "version", c.loadedVersion.Load())
		}
	}

	// Update client version info
	c.updateClientVersion()
	return nil
}

func (c *FileClient) safeAddress() string {
	s := strings.ReplaceAll(c.address, ":", "_")
	return strings.ReplaceAll(s, "/", "_")
}

func (c *FileClient) metaFilePath() string {
	return filepath.Join(c.getClientsDir(), prefixMeta+c.safeAddress()+suffixJSON)
}

func (c *FileClient) beatFilePath() string {
	return filepath.Join(c.getClientsDir(), prefixBeat+c.safeAddress()+suffixJSON)
}

func (c *FileClient) registerClient() error {
	clientsDir := c.getClientsDir()
	if err := os.MkdirAll(clientsDir, 0755); err != nil {
		return err
	}

	clientInfo := &dto.ClientInfo{
		Address:       c.address,
		App:           c.app,
		Lane:          c.lane,
		LeafNodes:     c.leafNodes,
		LastHeartbeat: time.Now().UnixMilli(),
		StartTime:     c.startTime,
		LoadedVersion: c.loadedVersion.Load(),
	}

	// Write m_{addr}.json (full info with leafNodes)
	if err := c.writeJsonFile(c.metaFilePath(), clientInfo); err != nil {
		return err
	}
	// Write b_{addr}.json (heartbeat)
	if err := c.writeBeatFile(); err != nil {
		return err
	}
	// Overwrite _latest.json on each registration
	if len(c.leafNodes) > 0 {
		_ = c.writeJsonFile(filepath.Join(clientsDir, "_latest.json"), clientInfo)
	}
	return nil
}

func (c *FileClient) unregisterClient() {
	// Delete mock directory first, then m_, then b_ last
	os.RemoveAll(c.getMockDir())
	os.Remove(c.metaFilePath())
	os.Remove(c.beatFilePath())
	log.Info(context.Background(), "client unregistered", "address", c.address)
}

func (c *FileClient) writeJsonFile(path string, v any) error {
	data, err := json.Marshal(v)
	if err != nil {
		return err
	}
	tmpPath := path + suffixTmp
	if err := os.WriteFile(tmpPath, data, 0644); err != nil {
		return err
	}
	return os.Rename(tmpPath, path)
}

func (c *FileClient) writeBeatFile() error {
	hb := map[string]int64{
		"lastHeartbeat": time.Now().UnixMilli(),
		"loadedVersion": c.loadedVersion.Load(),
	}
	return c.writeJsonFile(c.beatFilePath(), hb)
}

func (c *FileClient) updateClientVersion() {
	_ = c.writeBeatFile()
}

func (c *FileClient) updateHeartbeat() {
	if _, err := os.Stat(c.metaFilePath()); os.IsNotExist(err) {
		_ = c.registerClient()
		return
	}
	_ = c.writeBeatFile()
}

func (c *FileClient) getMockDir() string {
	safeAddr := strings.ReplaceAll(c.address, ":", "_")
	safeAddr = strings.ReplaceAll(safeAddr, "/", "_")
	return filepath.Join(c.storagePath, dirMock, strconv.Itoa(c.app), safeAddr)
}

func (c *FileClient) checkMocks(ctx context.Context) {
	mockDir := c.getMockDir()
	entries, err := os.ReadDir(mockDir)
	if err != nil {
		return // directory doesn't exist or can't be read
	}

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if !strings.HasSuffix(name, suffixJSON) || strings.HasSuffix(name, "_result"+suffixJSON) {
			continue
		}

		filePath := filepath.Join(mockDir, name)
		data, err := os.ReadFile(filePath)
		if err != nil {
			continue
		}

		var req dto.MockRequest
		if err := json.Unmarshal(data, &req); err != nil {
			log.Warn(ctx, "mock request parse failed", "file", name, "error", err)
			continue
		}

		// Delete request file first to prevent re-execution on crash
		os.Remove(filePath)

		result := c.executeMock(ctx, &req)

		// Write result file
		resultPath := filepath.Join(mockDir, req.MockId+"_result"+suffixJSON)
		resultData, err := json.Marshal(result)
		if err != nil {
			log.Error(ctx, "mock result marshal failed", "mockId", req.MockId, "error", err)
			continue
		}
		tmpPath := resultPath + suffixTmp
		if err := os.WriteFile(tmpPath, resultData, 0644); err != nil {
			log.Error(ctx, "mock result write failed", "mockId", req.MockId, "error", err)
			continue
		}
		os.Rename(tmpPath, resultPath)

		log.Info(ctx, "mock executed", "mockId", req.MockId, "success", result.Success)
	}
}

func (c *FileClient) executeMock(ctx context.Context, req *dto.MockRequest) *dto.MockResult {
	result := &dto.MockResult{
		MockId:    req.MockId,
		ExecuteAt: time.Now().UnixMilli(),
	}

	// Build roam with meta
	roam := icecontext.NewRoam()

	defer func() {
		if r := recover(); r != nil {
			result.Success = false
			result.Error = fmt.Sprintf("panic: %v", r)
			result.Trace = roam.GetTrace()
			result.Ts = roam.GetTs()
		}
	}()
	roam.SetId(req.IceId)
	roam.SetNid(req.ConfId)
	roam.SetScene(req.Scene)
	roam.SetDebug(req.Debug)
	if req.Ts > 0 {
		roam.SetTs(req.Ts)
	}

	// Put user roam data
	roam.PutAll(req.Roam)

	// Dispatch using cache directly (same logic as syncDispatcher)
	var handled bool
	if roam.GetId() > 0 && roam.GetNid() > 0 {
		// Both iceId and confId: get handler by iceId, find confId subtree
		h := cache.GetHandlerById(roam.GetId())
		if h != nil && h.Root != nil {
			if roam.GetDebug() == 0 {
				roam.SetDebug(h.Debug)
			}
			subtree := findNodeById(h.Root, roam.GetNid())
			if subtree != nil {
				sub := &handler.Handler{
					Debug:  roam.GetDebug(),
					Root:   subtree,
					ConfId: roam.GetNid(),
				}
				sub.HandleWithNodeId(ctx, roam)
				handled = true
			}
		}
	} else if roam.GetId() > 0 {
		h := cache.GetHandlerById(roam.GetId())
		if h != nil {
			if roam.GetDebug() == 0 {
				roam.SetDebug(h.Debug)
			}
			h.Handle(ctx, roam)
			handled = true
		}
	} else if roam.GetScene() != "" {
		handlerMap := cache.GetHandlersByScene(roam.GetScene())
		if len(handlerMap) > 0 {
			for _, h := range handlerMap {
				if roam.GetDebug() == 0 {
					roam.SetDebug(h.Debug)
				}
				roam.SetId(h.IceId)
				h.Handle(ctx, roam)
				handled = true
				break // mock only handles first matching handler
			}
		}
	} else if roam.GetNid() > 0 {
		root := cache.GetConfById(roam.GetNid())
		if root != nil {
			h := &handler.Handler{
				Debug:  roam.GetDebug(),
				Root:   root,
				ConfId: roam.GetNid(),
			}
			h.HandleWithNodeId(ctx, roam)
			handled = true
		}
	}

	if !handled {
		result.Success = false
		result.Error = "no matching handler found"
		result.Trace = roam.GetTrace()
		result.Ts = roam.GetTs()
		return result
	}

	result.Success = true
	result.Trace = roam.GetTrace()
	result.Ts = roam.GetTs()
	roamData := roam.Data()
	delete(roamData, "_ice")
	result.Roam = roamData
	if proc := roam.GetProcess(); proc != nil {
		result.Process = proc.String()
	}

	return result
}

// findNodeById walks the tree to find a node by its ID.
func findNodeById(n icenode.Node, id int64) icenode.Node {
	if n == nil {
		return nil
	}
	if n.GetNodeId() == id {
		return n
	}
	// Check forward
	if ba, ok := n.(icenode.BaseAccessor); ok {
		if fw := ba.GetForward(); fw != nil {
			if found := findNodeById(fw, id); found != nil {
				return found
			}
		}
	}
	// Check children (relation nodes)
	if rel, ok := n.(icenode.RelationNode); ok {
		children := rel.GetChildren()
		if children != nil {
			for x := children.First(); x != nil; x = x.Next {
				if found := findNodeById(x.Item, id); found != nil {
					return found
				}
			}
		}
	}
	return nil
}

func getAddress(app int) string {
	host := getHostIP()
	if host == "" {
		host = "unknown"
	}
	return host + "_" + uuid.GenerateAlphanumId(5)
}

func getHostIP() string {
	if ifaces, err := net.Interfaces(); err == nil {
		for _, iface := range ifaces {
			if iface.Flags&net.FlagLoopback != 0 {
				continue
			}
			if addrs, err := iface.Addrs(); err == nil {
				for _, addr := range addrs {
					if ipnet, ok := addr.(*net.IPNet); ok && ipnet.IP.To4() != nil {
						return ipnet.IP.String()
					}
				}
			}
		}
	}
	hostname, _ := os.Hostname()
	return hostname
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

// GetLane returns the swimlane name ("" means main trunk).
func (c *FileClient) GetLane() string {
	return c.lane
}

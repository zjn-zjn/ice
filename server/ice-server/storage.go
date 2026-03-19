package main

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Storage constants matching IceStorageConstants
const (
	dirApps     = "apps"
	dirBases    = "bases"
	dirConfs    = "confs"
	dirUpdates  = "updates"
	dirVersions = "versions"
	dirHistory  = "history"
	dirClients  = "clients"
	dirLane     = "lane"

	fileAppId   = "_id.txt"
	fileBaseId  = "_base_id.txt"
	fileConfId  = "_conf_id.txt"
	filePushId  = "_push_id.txt"
	fileVersion = "version.txt"

	suffixJson    = ".json"
	suffixTmp     = ".tmp"
	suffixUpd     = "_upd.json"
	latestClient  = "_latest.json"
)

// timeNowMs returns current time in milliseconds
func timeNowMs() int64 {
	return time.Now().UnixMilli()
}

type Storage struct {
	basePath       string
	appIdGenerator *IceIdGenerator

	baseIdGenerators sync.Map // int -> *IceIdGenerator
	confIdGenerators sync.Map
	pushIdGenerators sync.Map

	// basePathIndex: app -> (baseID -> relative dir path under bases/)
	// e.g. baseId=42, dir="反欺诈" means file is at bases/反欺诈/42.json
	// empty string "" means root bases/ directory
	basePathIndex sync.Map // int -> map[int64]string
	basePathMu    sync.Mutex
}

func NewStorage(basePath string) (*Storage, error) {
	s := &Storage{basePath: basePath}

	// Create base directories
	for _, dir := range []string{
		filepath.Join(basePath, dirApps),
		filepath.Join(basePath, dirClients),
	} {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return nil, err
		}
	}

	s.appIdGenerator = NewIceIdGenerator(filepath.Join(basePath, dirApps, fileAppId))
	log.Printf("ice file storage initialized at: %s", basePath)
	return s, nil
}

// ==================== App Operations ====================

func (s *Storage) NextAppId() (int, error) {
	id, err := s.appIdGenerator.NextId()
	return int(id), err
}

func (s *Storage) SaveApp(app *IceApp) error {
	path := filepath.Join(s.basePath, dirApps, fmt.Sprintf("%d%s", *app.ID, suffixJson))
	return writeJsonFile(path, app)
}

func (s *Storage) GetApp(appId int) (*IceApp, error) {
	path := filepath.Join(s.basePath, dirApps, fmt.Sprintf("%d%s", appId, suffixJson))
	return readJsonFileTyped[IceApp](path)
}

func (s *Storage) ListApps() ([]*IceApp, error) {
	dir := filepath.Join(s.basePath, dirApps)
	entries, err := os.ReadDir(dir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}

	var result []*IceApp
	for _, e := range entries {
		if !strings.HasSuffix(e.Name(), suffixJson) {
			continue
		}
		app, err := readJsonFileTyped[IceApp](filepath.Join(dir, e.Name()))
		if err != nil {
			log.Printf("failed to read app file: %s: %v", e.Name(), err)
			continue
		}
		if app != nil && app.Status != nil && *app.Status != StatusDeleted {
			result = append(result, app)
		}
	}
	return result, nil
}

// ==================== Base Operations ====================

func (s *Storage) getBaseIdGenerator(app int) *IceIdGenerator {
	if gen, ok := s.baseIdGenerators.Load(app); ok {
		return gen.(*IceIdGenerator)
	}
	s.ensureAppDirectories(app)
	gen := NewIceIdGenerator(filepath.Join(s.appPath(app), fileBaseId))
	actual, _ := s.baseIdGenerators.LoadOrStore(app, gen)
	return actual.(*IceIdGenerator)
}

func (s *Storage) NextBaseId(app int) (int64, error) {
	return s.getBaseIdGenerator(app).NextId()
}

func (s *Storage) EnsureBaseIdNotLessThan(app int, minId int64) error {
	return s.getBaseIdGenerator(app).EnsureNotLessThan(minId)
}

// BuildBasePathIndex walks bases/ recursively and builds the baseID→dirPath index for an app
func (s *Storage) BuildBasePathIndex(app int) {
	basesDir := filepath.Join(s.appPath(app), dirBases)
	index := make(map[int64]string)

	if _, err := os.Stat(basesDir); os.IsNotExist(err) {
		s.basePathIndex.Store(app, index)
		return
	}

	filepath.WalkDir(basesDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		if !strings.HasSuffix(d.Name(), suffixJson) {
			return nil
		}
		name := strings.TrimSuffix(d.Name(), suffixJson)
		id, err := strconv.ParseInt(name, 10, 64)
		if err != nil {
			return nil
		}
		// relative dir from bases/
		relPath, _ := filepath.Rel(basesDir, filepath.Dir(path))
		if relPath == "." {
			relPath = ""
		}
		index[id] = relPath
		return nil
	})

	s.basePathIndex.Store(app, index)
}

func (s *Storage) getBaseIndex(app int) map[int64]string {
	if idx, ok := s.basePathIndex.Load(app); ok {
		return idx.(map[int64]string)
	}
	// lazy build
	s.BuildBasePathIndex(app)
	if idx, ok := s.basePathIndex.Load(app); ok {
		return idx.(map[int64]string)
	}
	return make(map[int64]string)
}

func (s *Storage) setBaseIndexEntry(app int, baseId int64, relDir string) {
	s.basePathMu.Lock()
	defer s.basePathMu.Unlock()
	idx := s.getBaseIndex(app)
	// copy-on-write
	newIdx := make(map[int64]string, len(idx)+1)
	for k, v := range idx {
		newIdx[k] = v
	}
	newIdx[baseId] = relDir
	s.basePathIndex.Store(app, newIdx)
}

func (s *Storage) removeBaseIndexEntry(app int, baseId int64) {
	s.basePathMu.Lock()
	defer s.basePathMu.Unlock()
	idx := s.getBaseIndex(app)
	newIdx := make(map[int64]string, len(idx))
	for k, v := range idx {
		if k != baseId {
			newIdx[k] = v
		}
	}
	s.basePathIndex.Store(app, newIdx)
}

// resolveBasePath returns the full file path for a base, using index if available
func (s *Storage) resolveBasePath(app int, baseId int64) string {
	idx := s.getBaseIndex(app)
	relDir, ok := idx[baseId]
	if !ok {
		// fallback to root
		relDir = ""
	}
	if relDir == "" {
		return filepath.Join(s.appPath(app), dirBases, fmt.Sprintf("%d%s", baseId, suffixJson))
	}
	return filepath.Join(s.appPath(app), dirBases, relDir, fmt.Sprintf("%d%s", baseId, suffixJson))
}

func (s *Storage) SaveBase(base *IceBase) error {
	s.ensureAppDirectories(*base.App)
	path := s.resolveBasePath(*base.App, *base.ID)
	return writeJsonFile(path, base)
}

// SaveBaseAtPath saves a base file at a specific directory path relative to bases/
func (s *Storage) SaveBaseAtPath(base *IceBase, relDir string) error {
	s.ensureAppDirectories(*base.App)
	var path string
	if relDir == "" {
		path = filepath.Join(s.appPath(*base.App), dirBases, fmt.Sprintf("%d%s", *base.ID, suffixJson))
	} else {
		dir := filepath.Join(s.appPath(*base.App), dirBases, relDir)
		os.MkdirAll(dir, 0755)
		path = filepath.Join(dir, fmt.Sprintf("%d%s", *base.ID, suffixJson))
	}
	if err := writeJsonFile(path, base); err != nil {
		return err
	}
	s.setBaseIndexEntry(*base.App, *base.ID, relDir)
	return nil
}

func (s *Storage) GetBase(app int, baseId int64) (*IceBase, error) {
	path := s.resolveBasePath(app, baseId)
	return readJsonFileTyped[IceBase](path)
}

// ListBases lists all bases recursively under bases/ directory
func (s *Storage) ListBases(app int) ([]*IceBase, error) {
	basesDir := filepath.Join(s.appPath(app), dirBases)
	if _, err := os.Stat(basesDir); os.IsNotExist(err) {
		return nil, nil
	}

	var result []*IceBase
	filepath.WalkDir(basesDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		if !strings.HasSuffix(d.Name(), suffixJson) {
			return nil
		}
		// only load numeric-named json files (base files)
		name := strings.TrimSuffix(d.Name(), suffixJson)
		if _, parseErr := strconv.ParseInt(name, 10, 64); parseErr != nil {
			return nil
		}
		base, err := readJsonFileTyped[IceBase](path)
		if err != nil {
			log.Printf("failed to read base file: %s: %v", path, err)
			return nil
		}
		if base != nil {
			result = append(result, base)
		}
		return nil
	})
	return result, nil
}

func (s *Storage) ListActiveBases(app int) ([]*IceBase, error) {
	all, err := s.ListBases(app)
	if err != nil {
		return nil, err
	}
	var result []*IceBase
	for _, b := range all {
		if b.Status != nil && *b.Status == StatusOnline {
			result = append(result, b)
		}
	}
	return result, nil
}

func (s *Storage) DeleteBase(app int, baseId int64, hard bool) error {
	path := s.resolveBasePath(app, baseId)
	if hard {
		s.removeBaseIndexEntry(app, baseId)
		err := os.Remove(path)
		if err != nil && os.IsNotExist(err) {
			return nil
		}
		return err
	}
	base, err := readJsonFileTyped[IceBase](path)
	if err != nil || base == nil {
		return err
	}
	base.Status = Int8Ptr(StatusDeleted)
	now := timeNowMs()
	base.UpdateAt = &now
	return writeJsonFile(path, base)
}

// MoveBaseFile moves a base json file to a new directory
func (s *Storage) MoveBaseFile(app int, baseId int64, targetRelDir string) error {
	oldPath := s.resolveBasePath(app, baseId)
	var newPath string
	if targetRelDir == "" {
		newPath = filepath.Join(s.appPath(app), dirBases, fmt.Sprintf("%d%s", baseId, suffixJson))
	} else {
		targetDir := filepath.Join(s.appPath(app), dirBases, targetRelDir)
		os.MkdirAll(targetDir, 0755)
		newPath = filepath.Join(targetDir, fmt.Sprintf("%d%s", baseId, suffixJson))
	}
	if oldPath == newPath {
		return nil
	}
	if err := os.Rename(oldPath, newPath); err != nil {
		return err
	}
	s.setBaseIndexEntry(app, baseId, targetRelDir)
	return nil
}

// GetBaseRelDir returns the relative directory of a base file under bases/
func (s *Storage) GetBaseRelDir(app int, baseId int64) string {
	idx := s.getBaseIndex(app)
	return idx[baseId]
}

// RebuildBasePathIndexForDir rebuilds index entries for all bases under a given directory
func (s *Storage) RebuildBasePathIndexForDir(app int, relDir string) {
	basesDir := filepath.Join(s.appPath(app), dirBases)
	var walkDir string
	if relDir == "" {
		walkDir = basesDir
	} else {
		walkDir = filepath.Join(basesDir, relDir)
	}

	if _, err := os.Stat(walkDir); os.IsNotExist(err) {
		return
	}

	s.basePathMu.Lock()
	defer s.basePathMu.Unlock()

	idx := s.getBaseIndex(app)
	newIdx := make(map[int64]string, len(idx))
	for k, v := range idx {
		newIdx[k] = v
	}

	filepath.WalkDir(walkDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		if !strings.HasSuffix(d.Name(), suffixJson) {
			return nil
		}
		name := strings.TrimSuffix(d.Name(), suffixJson)
		id, parseErr := strconv.ParseInt(name, 10, 64)
		if parseErr != nil {
			return nil
		}
		rel, _ := filepath.Rel(basesDir, filepath.Dir(path))
		if rel == "." {
			rel = ""
		}
		newIdx[id] = rel
		return nil
	})

	s.basePathIndex.Store(app, newIdx)
}

// RemoveBaseIndexEntriesUnderDir removes all index entries for bases under a given directory
func (s *Storage) RemoveBaseIndexEntriesUnderDir(app int, relDir string) {
	s.basePathMu.Lock()
	defer s.basePathMu.Unlock()

	idx := s.getBaseIndex(app)
	newIdx := make(map[int64]string, len(idx))
	prefix := relDir + "/"
	for k, v := range idx {
		if v == relDir || strings.HasPrefix(v, prefix) {
			continue
		}
		newIdx[k] = v
	}
	s.basePathIndex.Store(app, newIdx)
}

// BasesDir returns the full path to the bases directory for an app
func (s *Storage) BasesDir(app int) string {
	return filepath.Join(s.appPath(app), dirBases)
}

// ==================== Conf Operations ====================

func (s *Storage) getConfIdGenerator(app int) *IceIdGenerator {
	if gen, ok := s.confIdGenerators.Load(app); ok {
		return gen.(*IceIdGenerator)
	}
	s.ensureAppDirectories(app)
	gen := NewIceIdGenerator(filepath.Join(s.appPath(app), fileConfId))
	actual, _ := s.confIdGenerators.LoadOrStore(app, gen)
	return actual.(*IceIdGenerator)
}

func (s *Storage) NextConfId(app int) (int64, error) {
	return s.getConfIdGenerator(app).NextId()
}

func (s *Storage) SaveConf(conf *IceConf) error {
	s.ensureAppDirectories(conf.App)
	path := filepath.Join(s.appPath(conf.App), dirConfs, fmt.Sprintf("%d%s", conf.ID, suffixJson))
	return writeJsonFile(path, conf)
}

func (s *Storage) GetConf(app int, confId int64) (*IceConf, error) {
	path := filepath.Join(s.appPath(app), dirConfs, fmt.Sprintf("%d%s", confId, suffixJson))
	return readJsonFileTyped[IceConf](path)
}

func (s *Storage) ListConfs(app int) ([]*IceConf, error) {
	dir := filepath.Join(s.appPath(app), dirConfs)
	return listJsonFiles[IceConf](dir)
}

func (s *Storage) ListActiveConfs(app int) ([]*IceConf, error) {
	all, err := s.ListConfs(app)
	if err != nil {
		return nil, err
	}
	var result []*IceConf
	for _, c := range all {
		if c.Status != nil && *c.Status != StatusDeleted {
			result = append(result, c)
		}
	}
	return result, nil
}

func (s *Storage) DeleteConf(app int, confId int64, hard bool) error {
	path := filepath.Join(s.appPath(app), dirConfs, fmt.Sprintf("%d%s", confId, suffixJson))
	if hard {
		err := os.Remove(path)
		if err != nil && os.IsNotExist(err) {
			return nil
		}
		return err
	}
	conf, err := readJsonFileTyped[IceConf](path)
	if err != nil || conf == nil {
		return err
	}
	conf.Status = Int8Ptr(StatusDeleted)
	now := timeNowMs()
	conf.UpdateAt = &now
	return writeJsonFile(path, conf)
}

// ==================== ConfUpdate Operations ====================

func (s *Storage) SaveConfUpdate(app int, iceId int64, confUpdate *IceConf) error {
	s.ensureAppDirectories(app)
	dir := filepath.Join(s.appPath(app), dirUpdates, strconv.FormatInt(iceId, 10))
	os.MkdirAll(dir, 0755)
	var confId int64
	if confUpdate.ConfId != nil {
		confId = *confUpdate.ConfId
	} else {
		confId = confUpdate.ID
	}
	path := filepath.Join(dir, fmt.Sprintf("%d%s", confId, suffixJson))
	return writeJsonFile(path, confUpdate)
}

func (s *Storage) GetConfUpdate(app int, iceId, confId int64) (*IceConf, error) {
	path := filepath.Join(s.appPath(app), dirUpdates, strconv.FormatInt(iceId, 10), fmt.Sprintf("%d%s", confId, suffixJson))
	return readJsonFileTyped[IceConf](path)
}

func (s *Storage) ListConfUpdates(app int, iceId int64) ([]*IceConf, error) {
	dir := filepath.Join(s.appPath(app), dirUpdates, strconv.FormatInt(iceId, 10))
	return listJsonFiles[IceConf](dir)
}

func (s *Storage) DeleteConfUpdate(app int, iceId, confId int64) error {
	path := filepath.Join(s.appPath(app), dirUpdates, strconv.FormatInt(iceId, 10), fmt.Sprintf("%d%s", confId, suffixJson))
	err := os.Remove(path)
	if err != nil && os.IsNotExist(err) {
		return nil
	}
	return err
}

func (s *Storage) DeleteAllConfUpdates(app int, iceId int64) error {
	dir := filepath.Join(s.appPath(app), dirUpdates, strconv.FormatInt(iceId, 10))
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return nil
	}
	return os.RemoveAll(dir)
}

// ==================== PushHistory Operations ====================

func (s *Storage) getPushIdGenerator(app int) *IceIdGenerator {
	if gen, ok := s.pushIdGenerators.Load(app); ok {
		return gen.(*IceIdGenerator)
	}
	s.ensureAppDirectories(app)
	gen := NewIceIdGenerator(filepath.Join(s.appPath(app), filePushId))
	actual, _ := s.pushIdGenerators.LoadOrStore(app, gen)
	return actual.(*IceIdGenerator)
}

func (s *Storage) NextPushId(app int) (int64, error) {
	return s.getPushIdGenerator(app).NextId()
}

func (s *Storage) SavePushHistory(h *IcePushHistory) error {
	s.ensureAppDirectories(h.App)
	path := filepath.Join(s.appPath(h.App), dirHistory, fmt.Sprintf("%d%s", *h.ID, suffixJson))
	return writeJsonFile(path, h)
}

func (s *Storage) GetPushHistory(app int, pushId int64) (*IcePushHistory, error) {
	path := filepath.Join(s.appPath(app), dirHistory, fmt.Sprintf("%d%s", pushId, suffixJson))
	return readJsonFileTyped[IcePushHistory](path)
}

func (s *Storage) ListPushHistories(app int, iceId *int64) ([]*IcePushHistory, error) {
	dir := filepath.Join(s.appPath(app), dirHistory)
	all, err := listJsonFiles[IcePushHistory](dir)
	if err != nil {
		return nil, err
	}

	var result []*IcePushHistory
	for _, h := range all {
		if iceId == nil || h.IceId == *iceId {
			result = append(result, h)
		}
	}

	// Sort newest first
	sort.Slice(result, func(i, j int) bool {
		ci, cj := int64(0), int64(0)
		if result[i].CreateAt != nil {
			ci = *result[i].CreateAt
		}
		if result[j].CreateAt != nil {
			cj = *result[j].CreateAt
		}
		return ci > cj
	})
	return result, nil
}

func (s *Storage) DeletePushHistory(app int, pushId int64) error {
	path := filepath.Join(s.appPath(app), dirHistory, fmt.Sprintf("%d%s", pushId, suffixJson))
	err := os.Remove(path)
	if err != nil && os.IsNotExist(err) {
		return nil
	}
	return err
}

// ==================== Version Operations ====================

func (s *Storage) GetVersion(app int) (int64, error) {
	path := filepath.Join(s.appPath(app), fileVersion)
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return 0, nil
		}
		return 0, err
	}
	content := strings.TrimSpace(string(data))
	if content == "" {
		return 0, nil
	}
	return strconv.ParseInt(content, 10, 64)
}

func (s *Storage) SetVersion(app int, version int64) error {
	s.ensureAppDirectories(app)
	path := filepath.Join(s.appPath(app), fileVersion)
	tmpPath := path + suffixTmp
	if err := os.WriteFile(tmpPath, []byte(strconv.FormatInt(version, 10)), 0644); err != nil {
		return err
	}
	return os.Rename(tmpPath, path)
}

func (s *Storage) SaveVersionUpdate(app int, version int64, dto *IceTransferDto) error {
	s.ensureAppDirectories(app)
	dir := filepath.Join(s.appPath(app), dirVersions)
	os.MkdirAll(dir, 0755)
	path := filepath.Join(dir, fmt.Sprintf("%d%s", version, suffixUpd))
	return writeJsonFile(path, dto)
}

func (s *Storage) CleanOldVersions(app int, retention int) error {
	dir := filepath.Join(s.appPath(app), dirVersions)
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return nil
	}
	currentVersion, err := s.GetVersion(app)
	if err != nil {
		return err
	}
	threshold := currentVersion - int64(retention)

	entries, err := os.ReadDir(dir)
	if err != nil {
		return err
	}
	for _, e := range entries {
		name := e.Name()
		if !strings.HasSuffix(name, suffixUpd) {
			continue
		}
		vStr := name[:len(name)-len(suffixUpd)]
		v, err := strconv.ParseInt(vStr, 10, 64)
		if err != nil {
			continue
		}
		if v < threshold {
			os.Remove(filepath.Join(dir, name))
		}
	}
	return nil
}

// ==================== Client Operations ====================

func (s *Storage) resolveClientsDir(app int, lane string) string {
	base := filepath.Join(s.basePath, dirClients, strconv.Itoa(app))
	if lane != "" {
		return filepath.Join(base, dirLane, lane)
	}
	return base
}

func safeAddress(address string) string {
	r := strings.NewReplacer(":", "_", "/", "_")
	return r.Replace(address)
}

func (s *Storage) SaveClient(client *IceClientInfo) error {
	clientsDir := s.resolveClientsDir(client.App, client.Lane)
	os.MkdirAll(clientsDir, 0755)

	path := filepath.Join(clientsDir, safeAddress(client.Address)+suffixJson)
	if err := writeJsonFile(path, client); err != nil {
		return err
	}

	if len(client.LeafNodes) > 0 {
		latestPath := filepath.Join(clientsDir, latestClient)
		if _, err := os.Stat(latestPath); os.IsNotExist(err) {
			writeJsonFile(latestPath, client)
		}
	}
	return nil
}

func (s *Storage) GetLatestClient(app int, lane string) (*IceClientInfo, error) {
	path := filepath.Join(s.resolveClientsDir(app, lane), latestClient)
	return readJsonFileTyped[IceClientInfo](path)
}

func (s *Storage) UpdateLatestClient(app int, lane string, client *IceClientInfo) error {
	if client == nil {
		return nil
	}
	clientsDir := s.resolveClientsDir(app, lane)
	os.MkdirAll(clientsDir, 0755)
	path := filepath.Join(clientsDir, latestClient)
	return writeJsonFile(path, client)
}

func (s *Storage) ListClients(app int, lane string) ([]*IceClientInfo, error) {
	clientsDir := s.resolveClientsDir(app, lane)
	if _, err := os.Stat(clientsDir); os.IsNotExist(err) {
		return nil, nil
	}

	entries, err := os.ReadDir(clientsDir)
	if err != nil {
		return nil, err
	}

	var result []*IceClientInfo
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), suffixJson) || e.Name() == latestClient {
			continue
		}
		client, err := readJsonFileTyped[IceClientInfo](filepath.Join(clientsDir, e.Name()))
		if err != nil {
			log.Printf("failed to read client file: %s: %v", e.Name(), err)
			continue
		}
		if client != nil {
			result = append(result, client)
		}
	}
	return result, nil
}

func (s *Storage) CountClients(app int, lane string) (int, error) {
	clientsDir := s.resolveClientsDir(app, lane)
	if _, err := os.Stat(clientsDir); os.IsNotExist(err) {
		return 0, nil
	}
	entries, err := os.ReadDir(clientsDir)
	if err != nil {
		return 0, err
	}
	count := 0
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), suffixJson) && e.Name() != latestClient {
			count++
		}
	}
	return count, nil
}

func (s *Storage) GetClient(app int, lane, address string) (*IceClientInfo, error) {
	path := filepath.Join(s.resolveClientsDir(app, lane), safeAddress(address)+suffixJson)
	return readJsonFileTyped[IceClientInfo](path)
}

func (s *Storage) FindFirstActiveClientWithLeafNodes(app int, lane string, timeoutMs int64) (*IceClientInfo, error) {
	clientsDir := s.resolveClientsDir(app, lane)
	if _, err := os.Stat(clientsDir); os.IsNotExist(err) {
		return nil, nil
	}

	now := timeNowMs()
	entries, err := os.ReadDir(clientsDir)
	if err != nil {
		return nil, err
	}

	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), suffixJson) || e.Name() == latestClient {
			continue
		}
		client, err := readJsonFileTyped[IceClientInfo](filepath.Join(clientsDir, e.Name()))
		if err != nil || client == nil {
			continue
		}
		if client.LastHeartbeat != nil && (now-*client.LastHeartbeat) < timeoutMs && len(client.LeafNodes) > 0 {
			return client, nil
		}
	}
	return nil, nil
}

func (s *Storage) DeleteClient(app int, lane, address string) error {
	path := filepath.Join(s.resolveClientsDir(app, lane), safeAddress(address)+suffixJson)
	err := os.Remove(path)
	if err != nil && os.IsNotExist(err) {
		return nil
	}
	return err
}

// ==================== Lane Operations ====================

func (s *Storage) ListLanes(app int) ([]string, error) {
	laneDir := filepath.Join(s.basePath, dirClients, strconv.Itoa(app), dirLane)
	if _, err := os.Stat(laneDir); os.IsNotExist(err) {
		return nil, nil
	}
	entries, err := os.ReadDir(laneDir)
	if err != nil {
		return nil, err
	}
	var result []string
	for _, e := range entries {
		if e.IsDir() {
			result = append(result, e.Name())
		}
	}
	return result, nil
}

func (s *Storage) DeleteEmptyLaneDir(app int, lane string) bool {
	laneDir := s.resolveClientsDir(app, lane)
	if _, err := os.Stat(laneDir); os.IsNotExist(err) {
		return false
	}

	entries, err := os.ReadDir(laneDir)
	if err != nil {
		return false
	}
	for _, e := range entries {
		if e.Name() != latestClient {
			return false
		}
	}

	os.Remove(filepath.Join(laneDir, latestClient))
	os.Remove(laneDir)

	parentLaneDir := filepath.Dir(laneDir)
	if filepath.Base(parentLaneDir) == dirLane {
		entries, err := os.ReadDir(parentLaneDir)
		if err == nil && len(entries) == 0 {
			os.Remove(parentLaneDir)
		}
	}
	return true
}

// ==================== Helper Methods ====================

func (s *Storage) appPath(app int) string {
	return filepath.Join(s.basePath, strconv.Itoa(app))
}

func (s *Storage) ensureAppDirectories(app int) {
	appPath := s.appPath(app)
	for _, dir := range []string{dirBases, dirConfs, dirUpdates, dirVersions, dirHistory} {
		os.MkdirAll(filepath.Join(appPath, dir), 0755)
	}
}

func writeJsonFile(path string, data interface{}) error {
	jsonData, err := json.Marshal(data)
	if err != nil {
		return err
	}
	os.MkdirAll(filepath.Dir(path), 0755)
	tmpPath := path + suffixTmp
	if err := os.WriteFile(tmpPath, jsonData, 0644); err != nil {
		return err
	}
	return os.Rename(tmpPath, path)
}

func readJsonFileTyped[T any](path string) (*T, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}
	var result T
	if err := json.Unmarshal(data, &result); err != nil {
		return nil, err
	}
	return &result, nil
}

func listJsonFiles[T any](dir string) ([]*T, error) {
	if _, err := os.Stat(dir); os.IsNotExist(err) {
		return nil, nil
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}
	var result []*T
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), suffixJson) {
			continue
		}
		item, err := readJsonFileTyped[T](filepath.Join(dir, e.Name()))
		if err != nil {
			log.Printf("failed to read file: %s: %v", e.Name(), err)
			continue
		}
		if item != nil {
			result = append(result, item)
		}
	}
	return result, nil
}

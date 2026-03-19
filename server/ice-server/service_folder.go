package main

import (
	"fmt"
	iofs "io/fs"
	"log"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
)

type FolderService struct {
	storage     *Storage
	baseService *BaseService
}

func NewFolderService(storage *Storage, baseService *BaseService) *FolderService {
	return &FolderService{storage: storage, baseService: baseService}
}

// ==================== Path Sanitization ====================

// sanitizePath validates and cleans a folder path to prevent directory traversal
func sanitizePath(p string) (string, error) {
	p = strings.TrimSpace(p)
	p = strings.Trim(p, "/")
	if p == "" {
		return "", nil
	}
	// reject path traversal
	if strings.Contains(p, "..") {
		return "", InputError("非法路径")
	}
	// reject special characters
	for _, seg := range strings.Split(p, "/") {
		if seg == "" || seg == "." {
			return "", InputError("非法路径")
		}
		// reject names starting with _ (reserved for system files like _base_id.txt)
		if strings.HasPrefix(seg, "_") {
			return "", InputError("文件夹名不能以_开头")
		}
	}
	return filepath.ToSlash(filepath.Clean(p)), nil
}

// sanitizeFolderName validates a folder name
func sanitizeFolderName(name string) error {
	name = strings.TrimSpace(name)
	if name == "" {
		return InputError("文件夹名不能为空")
	}
	if strings.Contains(name, "/") || strings.Contains(name, "\\") {
		return InputError("文件夹名不能包含路径分隔符")
	}
	if name == "." || name == ".." {
		return InputError("非法文件夹名")
	}
	if strings.HasPrefix(name, "_") {
		return InputError("文件夹名不能以_开头")
	}
	return nil
}

// ==================== Folder CRUD ====================

// FolderCreate creates a new folder (directory) under bases/{path}/{name}
func (fs *FolderService) FolderCreate(app int, parentPath, name string) error {
	parentPath, err := sanitizePath(parentPath)
	if err != nil {
		return err
	}
	if err := sanitizeFolderName(name); err != nil {
		return err
	}

	basesDir := fs.storage.BasesDir(app)
	var targetDir string
	if parentPath == "" {
		targetDir = filepath.Join(basesDir, name)
	} else {
		// verify parent exists
		parentDir := filepath.Join(basesDir, parentPath)
		if info, err := os.Stat(parentDir); err != nil || !info.IsDir() {
			return InputError("父目录不存在")
		}
		targetDir = filepath.Join(basesDir, parentPath, name)
	}

	if _, err := os.Stat(targetDir); err == nil {
		return AlreadyExist("文件夹 " + name)
	}

	fs.storage.ensureAppDirectories(app)
	return os.MkdirAll(targetDir, 0755)
}

// FolderRename renames a folder
func (fs *FolderService) FolderRename(app int, folderPath, newName string) error {
	folderPath, err := sanitizePath(folderPath)
	if err != nil {
		return err
	}
	if folderPath == "" {
		return InputError("不能重命名根目录")
	}
	if err := sanitizeFolderName(newName); err != nil {
		return err
	}

	basesDir := fs.storage.BasesDir(app)
	oldDir := filepath.Join(basesDir, folderPath)
	if info, err := os.Stat(oldDir); err != nil || !info.IsDir() {
		return IDNotExist("文件夹", folderPath)
	}

	parentDir := filepath.Dir(oldDir)
	newDir := filepath.Join(parentDir, newName)
	if _, err := os.Stat(newDir); err == nil {
		return AlreadyExist("文件夹 " + newName)
	}

	if err := os.Rename(oldDir, newDir); err != nil {
		return err
	}

	// rebuild index for the new directory since paths changed
	newRelDir, _ := filepath.Rel(basesDir, newDir)
	newRelDir = filepath.ToSlash(newRelDir)

	// remove old entries and rebuild
	fs.storage.RemoveBaseIndexEntriesUnderDir(app, folderPath)
	fs.storage.RebuildBasePathIndexForDir(app, newRelDir)

	return nil
}

// FolderDeleteResult holds counts of deleted items
type FolderDeleteResult struct {
	FolderCount int `json:"folderCount"`
	BaseCount   int `json:"baseCount"`
}

// FolderDelete recursively deletes a folder and all its contents
func (fs *FolderService) FolderDelete(app int, folderPath string) (*FolderDeleteResult, error) {
	folderPath, err := sanitizePath(folderPath)
	if err != nil {
		return nil, err
	}
	if folderPath == "" {
		return nil, InputError("不能删除根目录")
	}

	basesDir := fs.storage.BasesDir(app)
	targetDir := filepath.Join(basesDir, folderPath)
	if info, err := os.Stat(targetDir); err != nil || !info.IsDir() {
		return nil, IDNotExist("文件夹", folderPath)
	}

	// count items before deleting
	result := &FolderDeleteResult{}
	filepath.WalkDir(targetDir, func(path string, d iofs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() && path != targetDir {
			result.FolderCount++
		} else if !d.IsDir() && strings.HasSuffix(d.Name(), suffixJson) {
			name := strings.TrimSuffix(d.Name(), suffixJson)
			if _, parseErr := strconv.ParseInt(name, 10, 64); parseErr == nil {
				result.BaseCount++
			}
		}
		return nil
	})

	// remove index entries
	fs.storage.RemoveBaseIndexEntriesUnderDir(app, folderPath)

	// delete directory
	if err := os.RemoveAll(targetDir); err != nil {
		return nil, err
	}

	return result, nil
}

// FolderMove moves a folder to a new parent directory
func (fs *FolderService) FolderMove(app int, folderPath, targetPath string) error {
	folderPath, err := sanitizePath(folderPath)
	if err != nil {
		return err
	}
	if folderPath == "" {
		return InputError("不能移动根目录")
	}
	targetPath, err = sanitizePath(targetPath)
	if err != nil {
		return err
	}

	basesDir := fs.storage.BasesDir(app)
	srcDir := filepath.Join(basesDir, folderPath)
	if info, err := os.Stat(srcDir); err != nil || !info.IsDir() {
		return IDNotExist("文件夹", folderPath)
	}

	folderName := filepath.Base(srcDir)

	// prevent moving into self
	if targetPath == folderPath || strings.HasPrefix(targetPath+"/", folderPath+"/") {
		return InputError("不能将文件夹移动到自身或其子目录中")
	}

	var destDir string
	if targetPath == "" {
		destDir = filepath.Join(basesDir, folderName)
	} else {
		destParent := filepath.Join(basesDir, targetPath)
		if info, err := os.Stat(destParent); err != nil || !info.IsDir() {
			return IDNotExist("目标文件夹", targetPath)
		}
		destDir = filepath.Join(destParent, folderName)
	}

	if srcDir == destDir {
		return nil
	}
	if _, err := os.Stat(destDir); err == nil {
		return AlreadyExist("文件夹 " + folderName)
	}

	if err := os.Rename(srcDir, destDir); err != nil {
		return err
	}

	// rebuild index
	newRelDir, _ := filepath.Rel(basesDir, destDir)
	newRelDir = filepath.ToSlash(newRelDir)
	fs.storage.RemoveBaseIndexEntriesUnderDir(app, folderPath)
	fs.storage.RebuildBasePathIndexForDir(app, newRelDir)

	return nil
}

// ==================== Directory Listing ====================

// FolderItem represents a folder or base in a directory listing
type FolderItem struct {
	Type       string `json:"type"`                 // "folder" or "base"
	Name       string `json:"name"`                 // folder name or base name
	ID         *int64 `json:"id,omitempty"`          // base ID (only for type=base)
	ConfID     *int64 `json:"confId,omitempty"`      // base confId (only for type=base)
	Scenes     string `json:"scenes,omitempty"`      // base scenes
	Debug      *int8  `json:"debug,omitempty"`       // base debug
	ChildCount int    `json:"childCount,omitempty"`  // recursive base count (only for type=folder)
}

// FolderListResult is the response for folder/list
type FolderListResult struct {
	List       []*FolderItem `json:"list"`
	Total      int64         `json:"total"`
	PageNum    int           `json:"pageNum"`
	PageSize   int           `json:"pageSize"`
	Path       string        `json:"path"`
	ActualPath string        `json:"actualPath"`
}

// FolderList lists the direct children of a directory with pagination and search
func (fs *FolderService) FolderList(app int, path string, pageNum, pageSize int, nameFilter string) (*FolderListResult, error) {
	path, err := sanitizePath(path)
	if err != nil {
		return nil, err
	}

	basesDir := fs.storage.BasesDir(app)
	fs.storage.ensureAppDirectories(app)

	// Path fallback: walk up to find nearest existing ancestor
	actualPath := path
	targetDir := basesDir
	if path != "" {
		targetDir = filepath.Join(basesDir, path)
	}
	for {
		info, err := os.Stat(targetDir)
		if err == nil && info.IsDir() {
			break
		}
		if actualPath == "" {
			// already at root
			targetDir = basesDir
			break
		}
		// walk up one level
		idx := strings.LastIndex(actualPath, "/")
		if idx < 0 {
			actualPath = ""
			targetDir = basesDir
		} else {
			actualPath = actualPath[:idx]
			targetDir = filepath.Join(basesDir, actualPath)
		}
	}

	entries, err := os.ReadDir(targetDir)
	if err != nil {
		if os.IsNotExist(err) {
			return &FolderListResult{
				List:       []*FolderItem{},
				Total:      0,
				PageNum:    pageNum,
				PageSize:   pageSize,
				Path:       path,
				ActualPath: actualPath,
			}, nil
		}
		return nil, err
	}

	// Phase 1: Lightweight classification - only look at filenames, no JSON reads
	// os.ReadDir returns entries sorted by name ascending, iterate in reverse for descending order
	var folderNames []string
	var baseIDs []int64

	for i := len(entries) - 1; i >= 0; i-- {
		e := entries[i]
		if e.IsDir() {
			folderName := e.Name()
			if strings.HasPrefix(folderName, "_") || strings.HasPrefix(folderName, ".") {
				continue
			}
			if nameFilter != "" && !strings.Contains(folderName, nameFilter) {
				continue
			}
			folderNames = append(folderNames, folderName)
		} else if strings.HasSuffix(e.Name(), suffixJson) {
			name := strings.TrimSuffix(e.Name(), suffixJson)
			id, parseErr := strconv.ParseInt(name, 10, 64)
			if parseErr != nil {
				continue
			}
			baseIDs = append(baseIDs, id)
		}
	}

	// Sort baseIDs numerically descending (ReadDir sorts by string, not number)
	sort.Slice(baseIDs, func(i, j int) bool { return baseIDs[i] > baseIDs[j] })

	// Phase 2: If name filter, must read base JSONs to filter by name
	if nameFilter != "" {
		var filtered []int64
		for _, id := range baseIDs {
			base, err := readJsonFileTyped[IceBase](filepath.Join(targetDir, strconv.FormatInt(id, 10)+suffixJson))
			if err != nil || base == nil {
				continue
			}
			if base.Status != nil && *base.Status == StatusDeleted {
				continue
			}
			if strings.Contains(base.Name, nameFilter) {
				filtered = append(filtered, id)
			}
		}
		baseIDs = filtered
	}

	// Phase 3: Paginate over combined list (folders first, then bases)
	totalFolders := len(folderNames)
	total := totalFolders + len(baseIDs)
	start := (pageNum - 1) * pageSize
	end := start + pageSize
	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	// Phase 4: Only read data for current page items
	var pageItems []*FolderItem
	for idx := start; idx < end; idx++ {
		if idx < totalFolders {
			name := folderNames[idx]
			childCount := fs.countBasesRecursive(filepath.Join(targetDir, name))
			pageItems = append(pageItems, &FolderItem{
				Type:       "folder",
				Name:       name,
				ChildCount: childCount,
			})
		} else {
			id := baseIDs[idx-totalFolders]
			base, readErr := readJsonFileTyped[IceBase](filepath.Join(targetDir, strconv.FormatInt(id, 10)+suffixJson))
			if readErr != nil || base == nil {
				continue
			}
			if base.Status != nil && *base.Status == StatusDeleted {
				continue
			}
			idCopy := id
			pageItems = append(pageItems, &FolderItem{
				Type:   "base",
				Name:   base.Name,
				ID:     &idCopy,
				ConfID: base.ConfID,
				Scenes: base.Scenes,
				Debug:  base.Debug,
			})
		}
	}
	if pageItems == nil {
		pageItems = []*FolderItem{}
	}

	return &FolderListResult{
		List:       pageItems,
		Total:      int64(total),
		PageNum:    pageNum,
		PageSize:   pageSize,
		Path:       path,
		ActualPath: actualPath,
	}, nil
}

// countBasesRecursive counts all base json files recursively under a directory
func (fs *FolderService) countBasesRecursive(dir string) int {
	count := 0
	filepath.WalkDir(dir, func(path string, d iofs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return nil
		}
		if !strings.HasSuffix(d.Name(), suffixJson) {
			return nil
		}
		name := strings.TrimSuffix(d.Name(), suffixJson)
		if _, parseErr := strconv.ParseInt(name, 10, 64); parseErr == nil {
			count++
		}
		return nil
	})
	return count
}

// ==================== Folder Tree ====================

// FolderTreeNode represents a node in the folder tree
type FolderTreeNode struct {
	Name     string            `json:"name"`
	Path     string            `json:"path"`
	Children []*FolderTreeNode `json:"children"`
}

// FolderTree returns the recursive folder tree (no bases)
func (fs *FolderService) FolderTree(app int) ([]*FolderTreeNode, error) {
	basesDir := fs.storage.BasesDir(app)
	fs.storage.ensureAppDirectories(app)

	if _, err := os.Stat(basesDir); os.IsNotExist(err) {
		return []*FolderTreeNode{}, nil
	}

	return fs.buildFolderTree(basesDir, "")
}

func (fs *FolderService) buildFolderTree(dir, relativePath string) ([]*FolderTreeNode, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}

	var result []*FolderTreeNode
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		name := e.Name()
		if strings.HasPrefix(name, "_") || strings.HasPrefix(name, ".") {
			continue
		}

		var nodePath string
		if relativePath == "" {
			nodePath = name
		} else {
			nodePath = relativePath + "/" + name
		}

		children, err := fs.buildFolderTree(filepath.Join(dir, name), nodePath)
		if err != nil {
			children = []*FolderTreeNode{}
		}

		result = append(result, &FolderTreeNode{
			Name:     name,
			Path:     nodePath,
			Children: children,
		})
	}

	if result == nil {
		result = []*FolderTreeNode{}
	}

	sort.Slice(result, func(i, j int) bool { return result[i].Name < result[j].Name })
	return result, nil
}

// ==================== Batch Operations ====================

// BatchItem represents an item in a batch operation
type BatchItem struct {
	Type string `json:"type"` // "folder" or "base"
	Name string `json:"name,omitempty"` // folder name (for type=folder)
	ID   *int64 `json:"id,omitempty"`   // base ID (for type=base)
	Path string `json:"path,omitempty"` // folder path (for type=folder, used in batch operations)
}

// BatchMove moves multiple items to a target directory
func (fs *FolderService) BatchMove(app int, items []BatchItem, targetPath string) error {
	targetPath, err := sanitizePath(targetPath)
	if err != nil {
		return err
	}

	basesDir := fs.storage.BasesDir(app)

	// verify target exists (or is root)
	if targetPath != "" {
		targetDir := filepath.Join(basesDir, targetPath)
		if info, err := os.Stat(targetDir); err != nil || !info.IsDir() {
			return IDNotExist("目标文件夹", targetPath)
		}
	}

	for _, item := range items {
		switch item.Type {
		case "folder":
			folderPath := item.Path
			if folderPath == "" && item.Name != "" {
				folderPath = item.Name
			}
			if folderPath == "" {
				continue
			}
			if err := fs.FolderMove(app, folderPath, targetPath); err != nil {
				return err
			}
		case "base":
			if item.ID == nil {
				continue
			}
			if err := fs.storage.MoveBaseFile(app, *item.ID, targetPath); err != nil {
				return err
			}
		}
	}
	return nil
}

// BatchDelete deletes multiple items
func (fs *FolderService) BatchDelete(app int, items []BatchItem) error {
	for _, item := range items {
		switch item.Type {
		case "folder":
			folderPath := item.Path
			if folderPath == "" && item.Name != "" {
				folderPath = item.Name
			}
			if folderPath == "" {
				continue
			}
			if _, err := fs.FolderDelete(app, folderPath); err != nil {
				return err
			}
		case "base":
			if item.ID == nil {
				continue
			}
			if err := fs.storage.DeleteBase(app, *item.ID, true); err != nil {
				return err
			}
		}
	}
	return nil
}

// ExportFolder exports all bases recursively under a directory
func (fs *FolderService) ExportFolder(app int, path string) (string, error) {
	path, err := sanitizePath(path)
	if err != nil {
		return "", err
	}

	basesDir := fs.storage.BasesDir(app)
	var targetDir string
	if path == "" {
		targetDir = basesDir
	} else {
		targetDir = filepath.Join(basesDir, path)
	}

	if info, err := os.Stat(targetDir); err != nil || !info.IsDir() {
		return "", IDNotExist("文件夹", path)
	}

	// collect all base IDs recursively
	var baseIds []int64
	filepath.WalkDir(targetDir, func(p string, d iofs.DirEntry, err error) error {
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
		baseIds = append(baseIds, id)
		return nil
	})

	if len(baseIds) == 0 {
		return "[]", nil
	}

	data, err := fs.baseService.ExportBatchData(app, baseIds)
	if err != nil {
		return "", err
	}
	return data, nil
}

// ==================== Helper for base count info ====================

// CountItemsRecursive counts folders and bases recursively under a path
func (fs *FolderService) CountItemsRecursive(app int, path string) (folderCount, baseCount int) {
	basesDir := fs.storage.BasesDir(app)
	var targetDir string
	if path == "" {
		targetDir = basesDir
	} else {
		targetDir = filepath.Join(basesDir, path)
	}

	filepath.WalkDir(targetDir, func(p string, d iofs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		if d.IsDir() && p != targetDir {
			name := d.Name()
			if !strings.HasPrefix(name, "_") && !strings.HasPrefix(name, ".") {
				folderCount++
			}
		} else if !d.IsDir() && strings.HasSuffix(d.Name(), suffixJson) {
			name := strings.TrimSuffix(d.Name(), suffixJson)
			if _, parseErr := strconv.ParseInt(name, 10, 64); parseErr == nil {
				baseCount++
			}
		}
		return nil
	})
	return
}

func init() {
	// suppress unused import warnings
	_ = fmt.Sprintf
	_ = log.Println
}

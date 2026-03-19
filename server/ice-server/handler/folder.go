package handler

import (
	"net/http"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/service"
)

type FolderHandler struct {
	folderService *service.FolderService
	baseService   *service.BaseService
}

func NewFolderHandler(folderService *service.FolderService, baseService *service.BaseService) *FolderHandler {
	return &FolderHandler{folderService: folderService, baseService: baseService}
}

func (h *FolderHandler) Register(mux *http.ServeMux) {
	// Folder CRUD
	mux.HandleFunc("/ice-server/folder/create", WrapHandler(h.folderCreate))
	mux.HandleFunc("/ice-server/folder/rename", WrapHandler(h.folderRename))
	mux.HandleFunc("/ice-server/folder/delete", WrapHandler(h.folderDelete))
	mux.HandleFunc("/ice-server/folder/move", WrapHandler(h.folderMove))

	// Directory listing & tree
	mux.HandleFunc("/ice-server/folder/list", WrapHandler(h.folderList))
	mux.HandleFunc("/ice-server/folder/tree", WrapHandler(h.folderTree))

	// Batch operations
	mux.HandleFunc("/ice-server/base/batch/move", WrapHandler(h.batchMove))
	mux.HandleFunc("/ice-server/base/batch/delete", WrapHandler(h.batchDelete))

	// Folder export
	mux.HandleFunc("/ice-server/base/export/folder", WrapHandler(h.exportFolder))
}

// POST /ice-server/folder/create  body: { app, path, name }
func (h *FolderHandler) folderCreate(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body struct {
		App  int    `json:"app"`
		Path string `json:"path"`
		Name string `json:"name"`
	}
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("json body")
	}
	if body.App == 0 {
		return nil, model.InputError("app required")
	}
	if body.Name == "" {
		return nil, model.InputError("name required")
	}
	return nil, h.folderService.FolderCreate(body.App, body.Path, body.Name)
}

// POST /ice-server/folder/rename  body: { app, path, newName }
func (h *FolderHandler) folderRename(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body struct {
		App     int    `json:"app"`
		Path    string `json:"path"`
		NewName string `json:"newName"`
	}
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("json body")
	}
	if body.App == 0 {
		return nil, model.InputError("app required")
	}
	if body.Path == "" {
		return nil, model.InputError("path required")
	}
	if body.NewName == "" {
		return nil, model.InputError("newName required")
	}
	return nil, h.folderService.FolderRename(body.App, body.Path, body.NewName)
}

// POST /ice-server/folder/delete  body: { app, path }
func (h *FolderHandler) folderDelete(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body struct {
		App  int    `json:"app"`
		Path string `json:"path"`
	}
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("json body")
	}
	if body.App == 0 {
		return nil, model.InputError("app required")
	}
	if body.Path == "" {
		return nil, model.InputError("path required")
	}
	return h.folderService.FolderDelete(body.App, body.Path)
}

// POST /ice-server/folder/move  body: { app, path, targetPath }
func (h *FolderHandler) folderMove(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body struct {
		App        int    `json:"app"`
		Path       string `json:"path"`
		TargetPath string `json:"targetPath"`
	}
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("json body")
	}
	if body.App == 0 {
		return nil, model.InputError("app required")
	}
	if body.Path == "" {
		return nil, model.InputError("path required")
	}
	return nil, h.folderService.FolderMove(body.App, body.Path, body.TargetPath)
}

// GET /ice-server/folder/list?app=&path=&pageNum=&pageSize=&name=
func (h *FolderHandler) folderList(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	path := QueryStr(r, "path", "")
	pageNum := QueryInt(r, "pageNum", 1)
	pageSize := QueryInt(r, "pageSize", 20)
	name := QueryStr(r, "name", "")
	return h.folderService.FolderList(app, path, pageNum, pageSize, name)
}

// GET /ice-server/folder/tree?app=
func (h *FolderHandler) folderTree(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	return h.folderService.FolderTree(app)
}

// POST /ice-server/base/batch/move  body: { app, items, targetPath }
func (h *FolderHandler) batchMove(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body struct {
		App        int                 `json:"app"`
		Items      []service.BatchItem `json:"items"`
		TargetPath string              `json:"targetPath"`
	}
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("json body")
	}
	if body.App == 0 {
		return nil, model.InputError("app required")
	}
	if len(body.Items) == 0 {
		return nil, model.InputError("items required")
	}
	return nil, h.folderService.BatchMove(body.App, body.Items, body.TargetPath)
}

// POST /ice-server/base/batch/delete  body: { app, items }
func (h *FolderHandler) batchDelete(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body struct {
		App   int                 `json:"app"`
		Items []service.BatchItem `json:"items"`
	}
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("json body")
	}
	if body.App == 0 {
		return nil, model.InputError("app required")
	}
	if len(body.Items) == 0 {
		return nil, model.InputError("items required")
	}
	return nil, h.folderService.BatchDelete(body.App, body.Items)
}

// GET /ice-server/base/export/folder?app=&path=
func (h *FolderHandler) exportFolder(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	path := QueryStr(r, "path", "")
	data, exportErr := h.folderService.ExportFolder(app, path)
	if exportErr != nil {
		return nil, exportErr
	}
	return &model.WebResult{Ret: 0, Data: data}, nil
}

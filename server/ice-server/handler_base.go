package main

import (
	"encoding/json"
	"net/http"
)

type BaseHandler struct {
	baseService   *BaseService
	serverService *ServerService
}

func NewBaseHandler(baseService *BaseService, serverService *ServerService) *BaseHandler {
	return &BaseHandler{baseService: baseService, serverService: serverService}
}

func (h *BaseHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/base/list", wrapHandler(h.baseList))
	mux.HandleFunc("/ice-server/base/create", wrapHandler(h.baseCreate))
	mux.HandleFunc("/ice-server/base/edit", wrapHandler(h.baseEdit))
	mux.HandleFunc("/ice-server/base/delete", wrapHandler(h.baseDelete))
	mux.HandleFunc("/ice-server/base/backup", wrapHandler(h.basePush))
	mux.HandleFunc("/ice-server/base/backup/history", wrapHandler(h.history))
	mux.HandleFunc("/ice-server/base/backup/delete", wrapHandler(h.deleteHistory))
	mux.HandleFunc("/ice-server/base/export", wrapHandler(h.export))
	mux.HandleFunc("/ice-server/base/export/batch", wrapHandler(h.exportBatch))
	mux.HandleFunc("/ice-server/base/rollback", wrapHandler(h.rollback))
	mux.HandleFunc("/ice-server/base/import", wrapHandler(h.importData))
}

func (h *BaseHandler) baseList(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	search := &IceBaseSearch{
		App:      app,
		BaseId:   queryInt64(r, "id"),
		Name:     queryStr(r, "name", ""),
		Scene:    queryStr(r, "scene", ""),
		PageNum:  queryInt(r, "pageId", 1),
		PageSize: queryInt(r, "pageSize", 100),
	}
	return h.baseService.BaseList(search)
}

func (h *BaseHandler) baseCreate(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body IceBaseCreate
	if err := readJSONBody(r, &body); err != nil {
		return nil, InputError("base")
	}
	return h.baseService.BaseCreateAtPath(&body.IceBase, body.Path)
}

func (h *BaseHandler) baseEdit(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var base IceBase
	if err := readJSONBody(r, &base); err != nil {
		return nil, InputError("base")
	}
	return h.baseService.BaseEdit(&base)
}

func (h *BaseHandler) baseDelete(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	id, err := queryInt64Required(r, "id")
	if err != nil {
		return nil, err
	}
	return nil, h.baseService.storage.DeleteBase(app, id, true)
}

func (h *BaseHandler) basePush(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := queryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	reason := queryStr(r, "reason", "")
	return h.baseService.Push(app, iceId, reason)
}

func (h *BaseHandler) history(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := queryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	pageNum := queryInt(r, "pageNum", 1)
	pageSize := queryInt(r, "pageSize", 1000)
	return h.baseService.History(app, &iceId, pageNum, pageSize)
}

func (h *BaseHandler) deleteHistory(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	pushId, err := queryInt64Required(r, "pushId")
	if err != nil {
		return nil, err
	}
	return nil, h.baseService.Delete(app, pushId)
}

func (h *BaseHandler) export(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := queryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	pushId := queryInt64(r, "pushId")
	data, err := h.baseService.ExportData(app, iceId, pushId)
	if err != nil {
		return nil, err
	}
	return &WebResult{Ret: 0, Data: data}, nil
}

func (h *BaseHandler) exportBatch(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceIds := queryInt64List(r, "iceIds")
	data, err := h.baseService.ExportBatchData(app, iceIds)
	if err != nil {
		return nil, err
	}
	return &WebResult{Ret: 0, Data: data}, nil
}

func (h *BaseHandler) rollback(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	pushId, err := queryInt64Required(r, "pushId")
	if err != nil {
		return nil, err
	}
	return nil, h.baseService.Rollback(app, pushId)
}

func (h *BaseHandler) importData(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var body map[string]string
	if err := readJSONBody(r, &body); err != nil {
		return nil, InputError("json body")
	}
	jsonStr, ok := body["json"]
	if !ok {
		return nil, InputError("json field required")
	}
	var pushData PushData
	if err := json.Unmarshal([]byte(jsonStr), &pushData); err != nil {
		return nil, InputError("invalid json")
	}
	return nil, h.baseService.ImportData(&pushData)
}

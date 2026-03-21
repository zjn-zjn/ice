package handler

import (
	"encoding/json"
	"io"
	"net/http"
	"strings"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/service"
)

type BaseHandler struct {
	baseService   *service.BaseService
	serverService *service.ServerService
}

func NewBaseHandler(baseService *service.BaseService, serverService *service.ServerService) *BaseHandler {
	return &BaseHandler{baseService: baseService, serverService: serverService}
}

func (h *BaseHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/base/list", WrapHandler(h.baseList))
	mux.HandleFunc("/ice-server/base/create", WrapHandler(h.baseCreate))
	mux.HandleFunc("/ice-server/base/edit", WrapHandler(h.baseEdit))
	mux.HandleFunc("/ice-server/base/delete", WrapHandler(h.baseDelete))
	mux.HandleFunc("/ice-server/base/backup", WrapHandler(h.basePush))
	mux.HandleFunc("/ice-server/base/backup/history", WrapHandler(h.history))
	mux.HandleFunc("/ice-server/base/backup/delete", WrapHandler(h.deleteHistory))
	mux.HandleFunc("/ice-server/base/export", WrapHandler(h.export))
	mux.HandleFunc("/ice-server/base/export/batch", WrapHandler(h.exportBatch))
	mux.HandleFunc("/ice-server/base/rollback", WrapHandler(h.rollback))
	mux.HandleFunc("/ice-server/base/import", WrapHandler(h.importData))
}

func (h *BaseHandler) baseList(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	search := &model.IceBaseSearch{
		App:      app,
		BaseId:   QueryInt64(r, "id"),
		Name:     QueryStr(r, "name", ""),
		Scene:    QueryStr(r, "scene", ""),
		PageNum:  QueryInt(r, "pageId", 1),
		PageSize: QueryInt(r, "pageSize", 100),
	}
	return h.baseService.BaseList(search)
}

func (h *BaseHandler) baseCreate(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	var body model.IceBaseCreate
	if err := ReadJSONBody(r, &body); err != nil {
		return nil, model.InputError("base")
	}
	return h.baseService.BaseCreateAtPath(&body.IceBase, body.Path)
}

func (h *BaseHandler) baseEdit(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	var base model.IceBase
	if err := ReadJSONBody(r, &base); err != nil {
		return nil, model.InputError("base")
	}
	return h.baseService.BaseEdit(&base)
}

func (h *BaseHandler) baseDelete(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	id, err := QueryInt64Required(r, "id")
	if err != nil {
		return nil, err
	}
	return nil, h.baseService.DeleteBase(app, id)
}

func (h *BaseHandler) basePush(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := QueryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	reason := QueryStr(r, "reason", "")
	return h.baseService.Push(app, iceId, reason)
}

func (h *BaseHandler) history(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := QueryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	pageNum := QueryInt(r, "pageNum", 1)
	pageSize := QueryInt(r, "pageSize", 1000)
	return h.baseService.History(app, &iceId, pageNum, pageSize)
}

func (h *BaseHandler) deleteHistory(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	pushId, err := QueryInt64Required(r, "pushId")
	if err != nil {
		return nil, err
	}
	return nil, h.baseService.Delete(app, pushId)
}

func (h *BaseHandler) export(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := QueryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	pushId := QueryInt64(r, "pushId")
	data, err := h.baseService.ExportData(app, iceId, pushId)
	if err != nil {
		return nil, err
	}
	return &model.WebResult{Ret: 0, Data: data}, nil
}

func (h *BaseHandler) exportBatch(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceIds := QueryInt64List(r, "iceIds")
	data, err := h.baseService.ExportBatchData(app, iceIds)
	if err != nil {
		return nil, err
	}
	return &model.WebResult{Ret: 0, Data: data}, nil
}

func (h *BaseHandler) rollback(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	pushId, err := QueryInt64Required(r, "pushId")
	if err != nil {
		return nil, err
	}
	return nil, h.baseService.Rollback(app, pushId)
}

func (h *BaseHandler) importData(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	// Limit body size to 64MB
	r.Body = http.MaxBytesReader(w, r.Body, 64<<20)
	rawBody, err := io.ReadAll(r.Body)
	if err != nil {
		return nil, model.InputError("read body")
	}
	// Try to extract json field from {"json": "..."} wrapper (legacy format)
	var jsonStr string
	var wrapper struct {
		Json string `json:"json"`
	}
	if json.Unmarshal(rawBody, &wrapper) == nil && wrapper.Json != "" {
		jsonStr = wrapper.Json
	} else {
		jsonStr = string(rawBody)
	}
	trimmed := strings.TrimSpace(jsonStr)
	if strings.HasPrefix(trimmed, "[") {
		var pushDataList []*model.PushData
		if err := json.Unmarshal([]byte(trimmed), &pushDataList); err != nil {
			return nil, model.InputError("invalid json array")
		}
		for _, pushData := range pushDataList {
			if err := h.baseService.ImportData(pushData); err != nil {
				return nil, err
			}
		}
		return nil, nil
	}
	var pushData model.PushData
	if err := json.Unmarshal([]byte(trimmed), &pushData); err != nil {
		return nil, model.InputError("invalid json")
	}
	return nil, h.baseService.ImportData(&pushData)
}

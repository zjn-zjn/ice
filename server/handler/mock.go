package handler

import (
	"net/http"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/service"
)

type MockHandler struct {
	mockService   *service.MockService
	serverService *service.ServerService
}

func NewMockHandler(mockService *service.MockService, serverService *service.ServerService) *MockHandler {
	return &MockHandler{mockService: mockService, serverService: serverService}
}

func (h *MockHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/mock/execute", WrapHandler(h.execute))
	mux.HandleFunc("/ice-server/mock/schema", WrapHandler(h.schema))
}

func (h *MockHandler) execute(w http.ResponseWriter, r *http.Request) (any, error) {
	var req model.MockExecuteRequest
	if err := ReadJSONBody(r, &req); err != nil {
		return nil, err
	}
	if req.App <= 0 {
		panic(model.InputError("app is required"))
	}
	if req.IceId <= 0 && req.ConfId <= 0 && req.Scene == "" {
		panic(model.InputError("one of iceId, confId, scene is required"))
	}
	return h.mockService.Execute(&req)
}

func (h *MockHandler) schema(w http.ResponseWriter, r *http.Request) (any, error) {
	app := QueryInt(r, "app", 0)
	if app <= 0 {
		panic(model.InputError("app is required"))
	}
	iceId := int64(QueryInt(r, "iceId", 0))
	confId := int64(QueryInt(r, "confId", 0))
	if iceId <= 0 && confId <= 0 {
		panic(model.InputError("one of iceId or confId is required"))
	}
	lane := QueryStr(r, "lane", "")
	address := QueryStr(r, "address", "")
	return h.serverService.GetMockSchema(app, iceId, confId, lane, address), nil
}

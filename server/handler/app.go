package handler

import (
	"log"
	"net/http"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/service"
)

type AppHandler struct {
	appService    *service.AppService
	serverService *service.ServerService
}

func NewAppHandler(appService *service.AppService, serverService *service.ServerService) *AppHandler {
	return &AppHandler{appService: appService, serverService: serverService}
}

func (h *AppHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/app/list", WrapHandler(h.appList))
	mux.HandleFunc("/ice-server/app/edit", WrapHandler(h.appEdit))
	mux.HandleFunc("/ice-server/app/recycle", WrapHandler(h.recycle))
}

func (h *AppHandler) appList(w http.ResponseWriter, r *http.Request) (any, error) {
	pageNum := QueryInt(r, "pageNum", 1)
	pageSize := QueryInt(r, "pageSize", 20)
	name := QueryStr(r, "name", "")
	app := QueryIntPtr(r, "app")
	return h.appService.AppList(pageNum, pageSize, name, app)
}

func (h *AppHandler) appEdit(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	var app model.IceApp
	if err := ReadJSONBody(r, &app); err != nil {
		return nil, model.InputError("app")
	}
	return h.appService.AppEdit(&app)
}

func (h *AppHandler) recycle(w http.ResponseWriter, r *http.Request) (any, error) {
	app := QueryIntPtr(r, "app")
	go func() {
		defer func() {
			if rv := recover(); rv != nil {
				log.Printf("recycle panic: %v", rv)
			}
		}()
		h.serverService.Recycle(app)
	}()
	return nil, nil
}

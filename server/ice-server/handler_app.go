package main

import (
	"log"
	"net/http"
)

type AppHandler struct {
	appService    *AppService
	serverService *ServerService
}

func NewAppHandler(appService *AppService, serverService *ServerService) *AppHandler {
	return &AppHandler{appService: appService, serverService: serverService}
}

func (h *AppHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/app/list", wrapHandler(h.appList))
	mux.HandleFunc("/ice-server/app/edit", wrapHandler(h.appEdit))
	mux.HandleFunc("/ice-server/app/recycle", wrapHandler(h.recycle))
}

func (h *AppHandler) appList(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	pageNum := queryInt(r, "pageNum", 1)
	pageSize := queryInt(r, "pageSize", 1000)
	name := queryStr(r, "name", "")
	app := queryIntPtr(r, "app")
	return h.appService.AppList(pageNum, pageSize, name, app)
}

func (h *AppHandler) appEdit(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var app IceApp
	if err := readJSONBody(r, &app); err != nil {
		return nil, InputError("app")
	}
	return h.appService.AppEdit(&app)
}

func (h *AppHandler) recycle(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app := queryIntPtr(r, "app")
	go func() {
		defer func() {
			if r := recover(); r != nil {
				log.Printf("recycle panic: %v", r)
			}
		}()
		h.serverService.Recycle(app)
	}()
	return nil, nil
}

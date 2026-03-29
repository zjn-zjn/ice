package handler

import (
	"net/http"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/service"
)

type ConfHandler struct {
	confService   *service.ConfService
	serverService *service.ServerService
	appService    *service.AppService
	clientManager *service.ClientManager
}

func NewConfHandler(confService *service.ConfService, serverService *service.ServerService, appService *service.AppService, clientManager *service.ClientManager) *ConfHandler {
	return &ConfHandler{
		confService:   confService,
		serverService: serverService,
		appService:    appService,
		clientManager: clientManager,
	}
}

func (h *ConfHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/conf/edit", WrapHandler(h.confEdit))
	mux.HandleFunc("/ice-server/conf/leaf/class", WrapHandler(h.leafClass))
	mux.HandleFunc("/ice-server/conf/class/check", WrapHandler(h.classCheck))
	mux.HandleFunc("/ice-server/conf/lane/list", WrapHandler(h.laneList))
	mux.HandleFunc("/ice-server/conf/detail", WrapHandler(h.confDetail))
	mux.HandleFunc("/ice-server/conf/node-meta", WrapHandler(h.nodeMeta))
	mux.HandleFunc("/ice-server/conf/changes", WrapHandler(h.changes))
	mux.HandleFunc("/ice-server/conf/release", WrapHandler(h.release))
	mux.HandleFunc("/ice-server/conf/update_clean", WrapHandler(h.updateClean))
}

func (h *ConfHandler) confEdit(w http.ResponseWriter, r *http.Request) (any, error) {
	if err := RequirePost(r); err != nil {
		return nil, err
	}
	var editNode model.IceEditNode
	if err := ReadJSONBody(r, &editNode); err != nil {
		return nil, model.InputError("editNode")
	}
	return h.confService.ConfEdit(&editNode)
}

func (h *ConfHandler) leafClass(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	nodeType := QueryInt8(r, "type")
	if nodeType == nil {
		return nil, model.InputError("type required")
	}
	lane := QueryStr(r, "lane", "")
	return h.confService.GetConfLeafClass(app, *nodeType, lane), nil
}

func (h *ConfHandler) classCheck(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	clazz := QueryStr(r, "clazz", "")
	nodeType := QueryInt8(r, "type")
	if nodeType == nil {
		return nil, model.InputError("type required")
	}
	return nil, h.confService.LeafClassCheckAPI(app, clazz, *nodeType)
}

func (h *ConfHandler) laneList(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	return h.clientManager.ListLanes(app), nil
}

func (h *ConfHandler) confDetail(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := QueryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	confId := QueryInt64(r, "confId")
	lane := QueryStr(r, "lane", "")

	base := h.serverService.GetActiveBaseById(app, iceId)
	if base == nil {
		return nil, model.InputError("app|iceId")
	}

	actualConfId := int64(0)
	if confId != nil {
		actualConfId = *confId
	} else if base.ConfID != nil {
		actualConfId = *base.ConfID
	}

	address := QueryStr(r, "address", "server")
	activeOnly := QueryStr(r, "activeOnly", "") == "true"
	showConf, err := h.confService.ConfDetail(app, actualConfId, address, iceId, lane, activeOnly)
	if err != nil {
		return nil, err
	}
	showConf.IceId = iceId
	showConf.Name = base.Name
	return showConf, nil
}

func (h *ConfHandler) nodeMeta(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	lane := QueryStr(r, "lane", "")
	address := QueryStr(r, "address", "")

	result := make(map[string]any)
	registry := h.clientManager.GetClientRegistry(app)
	lanes := h.clientManager.ListLanes(app)
	result["clientRegistry"] = registry
	result["lanes"] = lanes

	actualLane := lane
	actualAddress := address
	var fallbackReason string

	// Check if lane exists
	if lane != "" {
		found := false
		for _, l := range lanes {
			if l == lane {
				found = true
				break
			}
		}
		if !found {
			actualLane = ""
			actualAddress = ""
			fallbackReason = "lane_not_found"
		}
	}

	// Check if address exists
	if actualAddress != "" {
		addressExists := false
		if registry != nil {
			var clients []*model.ShowClientInfo
			if actualLane == "" {
				clients = registry.MainClients
			} else if registry.LaneClients != nil {
				clients = registry.LaneClients[actualLane]
			}
			for _, c := range clients {
				if c.Address == actualAddress {
					addressExists = true
					break
				}
			}
		}
		if !addressExists {
			actualAddress = ""
			if fallbackReason == "" {
				fallbackReason = "address_not_found"
			}
		}
	}

	if actualAddress != "" {
		result["leafClassMap"] = h.clientManager.GetClientLeafClasses(app, actualLane)
	} else {
		result["leafClassMap"] = h.clientManager.GetAllLeafClasses(app, actualLane)
	}

	if fallbackReason != "" {
		result["fallbackReason"] = fallbackReason
		result["actualLane"] = actualLane
		result["actualAddress"] = actualAddress
	}

	return result, nil
}

func (h *ConfHandler) changes(w http.ResponseWriter, r *http.Request) (any, error) {
	app, err := QueryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := QueryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	confId := QueryInt64(r, "confId")
	items, err := h.serverService.GetChanges(app, iceId, confId)
	if err != nil {
		return nil, err
	}
	return map[string]any{"changes": items}, nil
}

func (h *ConfHandler) release(w http.ResponseWriter, r *http.Request) (any, error) {
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
	if _, err := h.serverService.Release(app, iceId); err != nil {
		return nil, err
	}
	return []string{}, nil
}

func (h *ConfHandler) updateClean(w http.ResponseWriter, r *http.Request) (any, error) {
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
	return nil, h.serverService.UpdateClean(app, iceId)
}

package main

import (
	"net/http"
)

type ConfHandler struct {
	confService   *ConfService
	serverService *ServerService
	appService    *AppService
	clientManager *ClientManager
}

func NewConfHandler(confService *ConfService, serverService *ServerService, appService *AppService, clientManager *ClientManager) *ConfHandler {
	return &ConfHandler{
		confService:   confService,
		serverService: serverService,
		appService:    appService,
		clientManager: clientManager,
	}
}

func (h *ConfHandler) Register(mux *http.ServeMux) {
	mux.HandleFunc("/ice-server/conf/edit", wrapHandler(h.confEdit))
	mux.HandleFunc("/ice-server/conf/leaf/class", wrapHandler(h.leafClass))
	mux.HandleFunc("/ice-server/conf/class/check", wrapHandler(h.classCheck))
	mux.HandleFunc("/ice-server/conf/lane/list", wrapHandler(h.laneList))
	mux.HandleFunc("/ice-server/conf/detail", wrapHandler(h.confDetail))
	mux.HandleFunc("/ice-server/conf/node-meta", wrapHandler(h.nodeMeta))
	mux.HandleFunc("/ice-server/conf/release", wrapHandler(h.release))
	mux.HandleFunc("/ice-server/conf/update_clean", wrapHandler(h.updateClean))
}

func (h *ConfHandler) confEdit(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	var editNode IceEditNode
	if err := readJSONBody(r, &editNode); err != nil {
		return nil, InputError("editNode")
	}
	return h.confService.ConfEdit(&editNode)
}

func (h *ConfHandler) leafClass(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	nodeType := queryInt8(r, "type")
	if nodeType == nil {
		return nil, InputError("type required")
	}
	lane := queryStr(r, "lane", "")
	return h.confService.GetConfLeafClass(app, *nodeType, lane), nil
}

func (h *ConfHandler) classCheck(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	clazz := queryStr(r, "clazz", "")
	nodeType := queryInt8(r, "type")
	if nodeType == nil {
		return nil, InputError("type required")
	}
	return nil, h.confService.LeafClassCheckAPI(app, clazz, *nodeType)
}

func (h *ConfHandler) laneList(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	return h.clientManager.ListLanes(app), nil
}

func (h *ConfHandler) confDetail(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := queryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	confId := queryInt64(r, "confId")
	lane := queryStr(r, "lane", "")

	base := h.serverService.GetActiveBaseById(app, iceId)
	if base == nil {
		return nil, InputError("app|iceId")
	}

	actualConfId := int64(0)
	if confId != nil {
		actualConfId = *confId
	} else if base.ConfID != nil {
		actualConfId = *base.ConfID
	}

	address := queryStr(r, "address", "server")
	showConf, err := h.confService.ConfDetail(app, actualConfId, address, iceId, lane)
	if err != nil {
		return nil, err
	}
	showConf.IceId = iceId
	return showConf, nil
}

func (h *ConfHandler) nodeMeta(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	lane := queryStr(r, "lane", "")
	address := queryStr(r, "address", "")

	result := make(map[string]interface{})
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
			var clients []*ShowClientInfo
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
		result["leafClassMap"] = h.clientManager.GetClientLeafClasses(app, actualAddress, actualLane)
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

func (h *ConfHandler) release(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := queryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	if _, err := h.serverService.Release(app, iceId); err != nil {
		return nil, err
	}
	return []string{}, nil
}

func (h *ConfHandler) updateClean(w http.ResponseWriter, r *http.Request) (interface{}, error) {
	app, err := queryIntRequired(r, "app")
	if err != nil {
		return nil, err
	}
	iceId, err := queryInt64Required(r, "iceId")
	if err != nil {
		return nil, err
	}
	return nil, h.serverService.UpdateClean(app, iceId)
}

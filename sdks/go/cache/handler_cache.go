package cache

import (
	"context"
	"strconv"
	"strings"
	"sync"

	"github.com/waitmoon/ice/sdks/go/dto"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/handler"
	"github.com/waitmoon/ice/sdks/go/log"
	"github.com/waitmoon/ice/sdks/go/node"
)

var (
	idHandlerMap      = make(map[int64]*handler.Handler)
	sceneHandlersMap  = make(map[string]map[int64]*handler.Handler)
	confIdHandlersMap = make(map[int64]map[int64]*handler.Handler)
	handlerMu         sync.RWMutex
)

// GetHandlerById returns a handler by ice ID.
func GetHandlerById(iceId int64) *handler.Handler {
	handlerMu.RLock()
	defer handlerMu.RUnlock()
	return idHandlerMap[iceId]
}

// GetHandlersByScene returns handlers by scene.
func GetHandlersByScene(scene string) map[int64]*handler.Handler {
	handlerMu.RLock()
	defer handlerMu.RUnlock()
	return sceneHandlersMap[scene]
}

// GetIdHandlerMap returns a copy of the id handler map.
func GetIdHandlerMap() map[int64]*handler.Handler {
	handlerMu.RLock()
	defer handlerMu.RUnlock()
	result := make(map[int64]*handler.Handler, len(idHandlerMap))
	for k, v := range idHandlerMap {
		result[k] = v
	}
	return result
}

// InsertOrUpdateHandlers inserts or updates handlers from DTOs.
func InsertOrUpdateHandlers(baseDtos []dto.BaseDto) []string {
	var errors []string

	for _, base := range baseDtos {
		h := handler.NewHandler()
		h.IceId = base.Id
		h.TimeType = enum.TimeType(base.TimeType)
		h.Start = base.Start
		h.End = base.End
		h.Debug = base.Debug

		confId := base.ConfId
		if confId != 0 {
			root := GetConfById(confId)
			if root == nil {
				errors = append(errors, "confId not exist: "+strconv.FormatInt(confId, 10))
				log.Error(context.Background(), "confId not exist", "confId", confId)
				continue
			}

			handlerMu.Lock()
			handlerMap := confIdHandlersMap[confId]
			if handlerMap == nil {
				handlerMap = make(map[int64]*handler.Handler)
				confIdHandlersMap[confId] = handlerMap
			}
			handlerMap[h.IceId] = h
			handlerMu.Unlock()

			h.Root = root
			h.ConfId = confId
		}

		if base.Scenes != "" {
			scenes := strings.Split(base.Scenes, ",")
			h.SetScenes(scenes)
		}

		onlineOrUpdateHandler(h)
	}

	return errors
}

// DeleteHandlers removes handlers by IDs.
func DeleteHandlers(ids []int64) {
	handlerMu.Lock()
	defer handlerMu.Unlock()

	for _, id := range ids {
		h := idHandlerMap[id]
		if h != nil {
			if h.Root != nil {
				delete(confIdHandlersMap, h.Root.GetNodeId())
			}
			offlineHandler(h)
		}
	}
}

// UpdateHandlerRoot updates the root node for handlers with the given conf node.
func UpdateHandlerRoot(confNode node.Node) {
	if confNode == nil {
		return
	}
	confId := confNode.GetNodeId()

	handlerMu.Lock()
	defer handlerMu.Unlock()

	handlerMap := confIdHandlersMap[confId]
	if handlerMap != nil {
		for _, h := range handlerMap {
			h.Root = confNode
		}
	}
}

func onlineOrUpdateHandler(h *handler.Handler) {
	handlerMu.Lock()
	defer handlerMu.Unlock()

	var originHandler *handler.Handler
	if h.IceId > 0 {
		originHandler = idHandlerMap[h.IceId]
		idHandlerMap[h.IceId] = h
	}

	// Remove from scenes that are no longer in the new handler
	if originHandler != nil && len(originHandler.Scenes) > 0 {
		if len(h.Scenes) == 0 {
			for scene := range originHandler.Scenes {
				handlerMap := sceneHandlersMap[scene]
				if handlerMap != nil {
					delete(handlerMap, originHandler.IceId)
					if len(handlerMap) == 0 {
						delete(sceneHandlersMap, scene)
					}
				}
			}
			return
		}
		for scene := range originHandler.Scenes {
			if !h.HasScene(scene) {
				handlerMap := sceneHandlersMap[scene]
				if handlerMap != nil {
					delete(handlerMap, originHandler.IceId)
					if len(handlerMap) == 0 {
						delete(sceneHandlersMap, scene)
					}
				}
			}
		}
	}

	// Add to new scenes
	for scene := range h.Scenes {
		handlerMap := sceneHandlersMap[scene]
		if handlerMap == nil {
			handlerMap = make(map[int64]*handler.Handler)
			sceneHandlersMap[scene] = handlerMap
		}
		handlerMap[h.IceId] = h
	}
}

func offlineHandler(h *handler.Handler) {
	if h == nil {
		return
	}
	delete(idHandlerMap, h.IceId)
	for scene := range h.Scenes {
		handlerMap := sceneHandlersMap[scene]
		if handlerMap != nil {
			delete(handlerMap, h.IceId)
			if len(handlerMap) == 0 {
				delete(sceneHandlersMap, scene)
			}
		}
	}
}

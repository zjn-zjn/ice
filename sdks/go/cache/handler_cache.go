package cache

import (
	"context"
	"strconv"
	"strings"
	"sync"

	"github.com/zjn-zjn/ice/sdks/go/dto"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/handler"
	"github.com/zjn-zjn/ice/sdks/go/log"
	"github.com/zjn-zjn/ice/sdks/go/node"
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

// GetHandlersByScene returns a copy of handlers by scene.
// Returns nil if no handlers exist for the scene.
func GetHandlersByScene(scene string) map[int64]*handler.Handler {
	handlerMu.RLock()
	defer handlerMu.RUnlock()
	original := sceneHandlersMap[scene]
	if original == nil {
		return nil
	}
	// Return a copy to prevent external modification
	result := make(map[int64]*handler.Handler, len(original))
	for k, v := range original {
		result[k] = v
	}
	return result
}

// GetHandlerByConfId returns handlers using the given conf ID.
func GetHandlerByConfId(confId int64) map[int64]*handler.Handler {
	handlerMu.RLock()
	defer handlerMu.RUnlock()
	original := confIdHandlersMap[confId]
	if original == nil {
		return nil
	}
	result := make(map[int64]*handler.Handler, len(original))
	for k, v := range original {
		result[k] = v
	}
	return result
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
// This method mirrors Java's IceHandlerCache.insertOrUpdate logic.
func InsertOrUpdateHandlers(baseDtos []dto.BaseDto) []string {
	var errors []string

	handlerMu.Lock()
	defer handlerMu.Unlock()

	for _, base := range baseDtos {
		h := handler.NewHandler()
		h.IceId = base.Id
		h.TimeType = enum.TimeType(base.TimeType)
		h.Start = base.Start
		h.End = base.End
		h.Debug = base.Debug

		confId := base.ConfId
		if confId != 0 {
			// Note: We need to get conf without holding handlerMu to avoid deadlock
			// But since we're already holding the lock, we use internal access
			confMu.RLock()
			root := confMap[confId]
			confMu.RUnlock()

			if root == nil {
				errors = append(errors, "confId not exist: "+strconv.FormatInt(confId, 10))
				log.Error(context.Background(), "confId not exist", "confId", confId)
				continue
			}

			handlerMap := confIdHandlersMap[confId]
			if handlerMap == nil {
				handlerMap = make(map[int64]*handler.Handler)
				confIdHandlersMap[confId] = handlerMap
			}
			handlerMap[h.IceId] = h

			h.Root = root
			h.ConfId = confId
		}

		if base.Scenes != "" {
			scenes := strings.Split(base.Scenes, ",")
			h.SetScenes(scenes)
		}

		onlineOrUpdateHandlerLocked(h)
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
			// Remove from confIdHandlersMap - only remove this handler, not the entire map
			// (Java lines 97-99)
			if h.ConfId != 0 {
				if handlerMap := confIdHandlersMap[h.ConfId]; handlerMap != nil {
					delete(handlerMap, h.IceId)
					if len(handlerMap) == 0 {
						delete(confIdHandlersMap, h.ConfId)
					}
				}
			}
			offlineHandlerLocked(h)
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

// onlineOrUpdateHandlerLocked adds or updates a handler. Must be called with handlerMu held.
func onlineOrUpdateHandlerLocked(h *handler.Handler) {
	var originHandler *handler.Handler
	if h.IceId > 0 {
		originHandler = idHandlerMap[h.IceId]
		idHandlerMap[h.IceId] = h
	}

	// Remove from scenes that are no longer in the new handler (Java lines 110-136)
	if originHandler != nil && len(originHandler.Scenes) > 0 {
		if len(h.Scenes) == 0 {
			// New handler has no scenes, remove from all old scenes
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
		// Remove from scenes that exist in old but not in new
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

	// Add to new scenes (Java lines 137-144)
	for scene := range h.Scenes {
		handlerMap := sceneHandlersMap[scene]
		if handlerMap == nil {
			handlerMap = make(map[int64]*handler.Handler)
			sceneHandlersMap[scene] = handlerMap
		}
		handlerMap[h.IceId] = h
	}
}

// offlineHandlerLocked removes a handler from all maps. Must be called with handlerMu held.
func offlineHandlerLocked(h *handler.Handler) {
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

// ClearAllHandlers clears all handler caches. Used for testing.
func ClearAllHandlers() {
	handlerMu.Lock()
	defer handlerMu.Unlock()

	idHandlerMap = make(map[int64]*handler.Handler)
	sceneHandlersMap = make(map[string]map[int64]*handler.Handler)
	confIdHandlersMap = make(map[int64]map[int64]*handler.Handler)
}

package service

import (
	"log"
	"sort"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/storage"
)

type ClientManager struct {
	config  *config.Config
	storage *storage.Storage
}

func NewClientManager(config *config.Config, storage *storage.Storage) *ClientManager {
	return &ClientManager{config: config, storage: storage}
}

func (cm *ClientManager) GetActiveClients(app int, lane string) ([]*model.IceClientInfo, error) {
	beats, err := cm.storage.ListClientBeats(app, lane)
	if err != nil {
		return nil, err
	}
	timeoutMs := cm.config.ClientTimeout.Milliseconds()
	now := model.TimeNowMs()
	var result []*model.IceClientInfo
	for addr, hb := range beats {
		if hb.LastHeartbeat != nil && (now-*hb.LastHeartbeat) < timeoutMs {
			result = append(result, &model.IceClientInfo{
				Address:       addr,
				LastHeartbeat: hb.LastHeartbeat,
				LoadedVersion: hb.LoadedVersion,
			})
		}
	}
	return result, nil
}

func (cm *ClientManager) GetRegisterClients(app int) map[string]bool {
	clients, err := cm.GetActiveClients(app, "")
	if err != nil {
		log.Printf("failed to get register clients for app:%d: %v", app, err)
		return nil
	}
	if len(clients) == 0 {
		return nil
	}
	result := make(map[string]bool, len(clients))
	for _, c := range clients {
		result[c.Address] = true
	}
	return result
}

func (cm *ClientManager) getValidLatestClient(app int, lane string) *model.IceClientInfo {
	latestClient, err := cm.storage.GetLatestClient(app, lane)
	if err != nil || latestClient == nil {
		return nil
	}

	currentClient, _ := cm.storage.GetClient(app, lane, latestClient.Address)
	if currentClient != nil && cm.isClientActive(currentClient) {
		return latestClient
	}

	activeAddr, _ := cm.storage.FindFirstActiveClient(app, lane, cm.config.ClientTimeout.Milliseconds())
	if activeAddr != "" {
		cm.storage.UpdateLatestClient(app, lane, activeAddr)
		log.Printf("updated _latest.json for app:%d lane:%s from inactive %s to active %s",
			app, lane, latestClient.Address, activeAddr)
		// Re-read updated _latest.json
		updated, _ := cm.storage.GetLatestClient(app, lane)
		if updated != nil {
			return updated
		}
	}
	return latestClient
}

func (cm *ClientManager) isClientActive(client *model.IceClientInfo) bool {
	if client == nil || client.LastHeartbeat == nil {
		return false
	}
	return (model.TimeNowMs() - *client.LastHeartbeat) < cm.config.ClientTimeout.Milliseconds()
}

func (cm *ClientManager) GetLeafTypeClasses(app int, nodeType int8, lane string) map[string]*model.LeafNodeInfo {
	result := make(map[string]*model.LeafNodeInfo)

	// Load trunk leafNodes
	trunkClient := cm.getValidLatestClient(app, "")
	if trunkClient != nil {
		for _, info := range trunkClient.LeafNodes {
			if info.Type == nodeType {
				result[info.Clazz] = copyNodeInfo(info)
			}
		}
	}

	// If lane specified, load lane leafNodes (overrides trunk)
	if lane != "" {
		laneClient := cm.getValidLatestClient(app, lane)
		if laneClient != nil {
			for _, info := range laneClient.LeafNodes {
				if info.Type == nodeType {
					result[info.Clazz] = copyNodeInfo(info)
				}
			}
		}
	}

	return result
}

func (cm *ClientManager) GetNodeInfo(app int, address, clazz string, nodeType int8, lane string) *model.LeafNodeInfo {
	// If address specified, try that specific client first
	if address != "" {
		result := cm.findNodeInfoByAddress(app, address, clazz, nodeType)
		if result != nil {
			return result
		}
	}
	// If lane specified, try lane first
	if lane != "" {
		result := cm.findNodeInfoInClient(app, lane, clazz, nodeType)
		if result != nil {
			return result
		}
	}
	// Fallback to trunk
	return cm.findNodeInfoInClient(app, "", clazz, nodeType)
}

// IsClientActive checks if a specific client address is currently active.
func (cm *ClientManager) IsClientActive(app int, lane, address string) bool {
	client, _ := cm.storage.GetClient(app, lane, address)
	return cm.isClientActive(client)
}

func (cm *ClientManager) findNodeInfoByAddress(app int, address string, clazz string, nodeType int8) *model.LeafNodeInfo {
	// Try to find the client in trunk first, then all lanes
	lanes := []string{""}
	if laneList := cm.ListLanes(app); len(laneList) > 0 {
		lanes = append(lanes, laneList...)
	}
	for _, lane := range lanes {
		client, _ := cm.storage.GetClient(app, lane, address)
		if client == nil || !cm.isClientActive(client) {
			continue
		}
		for _, info := range client.LeafNodes {
			if info.Clazz == clazz && info.Type == nodeType {
				return copyNodeInfo(info)
			}
		}
	}
	return nil
}

func (cm *ClientManager) findNodeInfoInClient(app int, lane, clazz string, nodeType int8) *model.LeafNodeInfo {
	latestClient := cm.getValidLatestClient(app, lane)
	if latestClient == nil || len(latestClient.LeafNodes) == 0 {
		return nil
	}
	for _, info := range latestClient.LeafNodes {
		if info.Clazz == clazz && info.Type == nodeType {
			return copyNodeInfo(info)
		}
	}
	return nil
}

func (cm *ClientManager) ListLanes(app int) []string {
	lanes, err := cm.storage.ListLanes(app)
	if err != nil {
		log.Printf("failed to list lanes for app:%d: %v", app, err)
		return nil
	}
	return lanes
}

func (cm *ClientManager) GetClientRegistry(app int) *model.ClientRegistryInfo {
	registry := &model.ClientRegistryInfo{}

	mainClients, err := cm.GetActiveClients(app, "")
	if err == nil {
		registry.MainClients = toShowClientInfoList(mainClients)
	}

	lanes := cm.ListLanes(app)
	if len(lanes) > 0 {
		laneMap := make(map[string][]*model.ShowClientInfo)
		for _, lane := range lanes {
			laneClients, err := cm.GetActiveClients(app, lane)
			if err == nil && len(laneClients) > 0 {
				laneMap[lane] = toShowClientInfoList(laneClients)
			}
		}
		if len(laneMap) > 0 {
			registry.LaneClients = laneMap
		}
	}
	return registry
}

func toShowClientInfoList(clients []*model.IceClientInfo) []*model.ShowClientInfo {
	if len(clients) == 0 {
		return []*model.ShowClientInfo{}
	}
	result := make([]*model.ShowClientInfo, len(clients))
	for i, c := range clients {
		result[i] = &model.ShowClientInfo{Address: c.Address}
	}
	return result
}

func (cm *ClientManager) GetAllLeafClasses(app int, lane string) map[int8][]*model.LeafNodeInfo {
	result := make(map[int8][]*model.LeafNodeInfo)
	types := []int8{5, 6, 7}
	for _, t := range types {
		clazzMap := cm.GetLeafTypeClasses(app, t, lane)
		if len(clazzMap) > 0 {
			var list []*model.LeafNodeInfo
			for clazz, info := range clazzMap {
				copied := copyNodeInfo(info)
				if copied != nil {
					copied.Clazz = clazz
					copied.Type = t
					if copied.Order == nil {
						copied.Order = model.IntPtr(100)
					}
				}
				if copied != nil {
					list = append(list, copied)
				} else {
					list = append(list, info)
				}
			}
			sort.Slice(list, func(i, j int) bool {
				return list[i].GetOrder() < list[j].GetOrder()
			})
			result[t] = list
		}
	}
	return result
}

func (cm *ClientManager) GetClientLeafClasses(app int, lane string) map[int8][]*model.LeafNodeInfo {
	return cm.GetAllLeafClasses(app, lane)
}

// CleanInactiveClients removes stale client files
func (cm *ClientManager) CleanInactiveClients() {
	apps, err := cm.storage.ListApps()
	if err != nil {
		log.Printf("failed to list apps for cleanup: %v", err)
		return
	}
	for _, app := range apps {
		if app.ID == nil {
			continue
		}
		cm.cleanInactiveClientsForApp(*app.ID, "")
		lanes := cm.ListLanes(*app.ID)
		for _, lane := range lanes {
			cm.cleanInactiveClientsForApp(*app.ID, lane)
		}
	}
}

func (cm *ClientManager) cleanInactiveClientsForApp(app int, lane string) {
	beats, err := cm.storage.ListClientBeats(app, lane)
	if err != nil {
		log.Printf("failed to list client beats for app:%d lane:%s: %v", app, lane, err)
		return
	}
	if len(beats) == 0 {
		if lane != "" {
			cm.storage.DeleteEmptyLaneDir(app, lane)
		}
		return
	}

	timeoutMs := cm.config.ClientTimeout.Milliseconds()
	now := model.TimeNowMs()

	var activeAddrs, inactiveAddrs []string
	for addr, hb := range beats {
		if hb.LastHeartbeat != nil && (now-*hb.LastHeartbeat) < timeoutMs {
			activeAddrs = append(activeAddrs, addr)
		} else {
			inactiveAddrs = append(inactiveAddrs, addr)
		}
	}

	if len(inactiveAddrs) == 0 {
		return
	}

	// Update _latest.json if it points to an inactive client
	latestClient, _ := cm.storage.GetLatestClient(app, lane)
	if latestClient != nil && len(activeAddrs) > 0 {
		for _, addr := range inactiveAddrs {
			if latestClient.Address == addr {
				cm.storage.UpdateLatestClient(app, lane, activeAddrs[0])
				break
			}
		}
	}

	deleteCount := 0
	for i, addr := range inactiveAddrs {
		if len(activeAddrs) == 0 && i == len(inactiveAddrs)-1 {
			if lane != "" {
				cm.storage.DeleteClient(app, lane, addr)
				deleteCount++
			} else {
				log.Printf("preserved last inactive client for app:%d, address:%s", app, addr)
			}
			continue
		}
		cm.storage.DeleteClient(app, lane, addr)
		deleteCount++
	}

	if deleteCount > 0 {
		log.Printf("cleaned %d inactive clients for app:%d lane:%s", deleteCount, app, lane)
	}

	if lane != "" {
		cm.storage.DeleteEmptyLaneDir(app, lane)
	}
}

func copyNodeInfo(source *model.LeafNodeInfo) *model.LeafNodeInfo {
	if source == nil {
		return nil
	}
	result := &model.LeafNodeInfo{
		Name:     source.Name,
		Clazz:    source.Clazz,
		Type:     source.Type,
		Desc:     source.Desc,
		Order:    source.Order,
		RoamKeys: source.RoamKeys,
	}
	if source.IceFields != nil {
		result.IceFields = make([]*model.IceFieldInfo, len(source.IceFields))
		for i, f := range source.IceFields {
			result.IceFields[i] = &model.IceFieldInfo{
				Field: f.Field,
				Type:  f.Type,
				Name:  f.Name,
				Desc:  f.Desc,
			}
		}
	}
	if source.HideFields != nil {
		result.HideFields = make([]*model.IceFieldInfo, len(source.HideFields))
		for i, f := range source.HideFields {
			result.HideFields[i] = &model.IceFieldInfo{
				Field: f.Field,
				Type:  f.Type,
				Name:  f.Name,
				Desc:  f.Desc,
			}
		}
	}
	return result
}

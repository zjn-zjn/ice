package main

import (
	"log"
	"sort"
)

type ClientManager struct {
	config  *Config
	storage *Storage
}

func NewClientManager(config *Config, storage *Storage) *ClientManager {
	return &ClientManager{config: config, storage: storage}
}

func (cm *ClientManager) GetActiveClients(app int, lane string) ([]*IceClientInfo, error) {
	clients, err := cm.storage.ListClients(app, lane)
	if err != nil {
		return nil, err
	}
	timeoutMs := cm.config.ClientTimeout.Milliseconds()
	now := timeNowMs()
	var result []*IceClientInfo
	for _, c := range clients {
		if c.LastHeartbeat != nil && (now-*c.LastHeartbeat) < timeoutMs {
			result = append(result, c)
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

func (cm *ClientManager) getValidLatestClient(app int, lane string) *IceClientInfo {
	latestClient, err := cm.storage.GetLatestClient(app, lane)
	if err != nil || latestClient == nil {
		return nil
	}

	currentClient, _ := cm.storage.GetClient(app, lane, latestClient.Address)
	if currentClient != nil && cm.isClientActive(currentClient) {
		return latestClient
	}

	newActive, _ := cm.storage.FindFirstActiveClientWithLeafNodes(app, lane, cm.config.ClientTimeout.Milliseconds())
	if newActive != nil {
		cm.storage.UpdateLatestClient(app, lane, newActive)
		log.Printf("updated _latest.json for app:%d lane:%s from inactive %s to active %s",
			app, lane, latestClient.Address, newActive.Address)
		return newActive
	}
	return latestClient
}

func (cm *ClientManager) isClientActive(client *IceClientInfo) bool {
	if client == nil || client.LastHeartbeat == nil {
		return false
	}
	return (timeNowMs() - *client.LastHeartbeat) < cm.config.ClientTimeout.Milliseconds()
}

func (cm *ClientManager) GetLeafTypeClasses(app int, nodeType int8, lane string) map[string]*LeafNodeInfo {
	result := make(map[string]*LeafNodeInfo)

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

func (cm *ClientManager) GetNodeInfo(app int, address, clazz string, nodeType int8, lane string) *LeafNodeInfo {
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

func (cm *ClientManager) findNodeInfoInClient(app int, lane, clazz string, nodeType int8) *LeafNodeInfo {
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

func (cm *ClientManager) GetClientRegistry(app int) *ClientRegistryInfo {
	registry := &ClientRegistryInfo{}

	mainClients, err := cm.GetActiveClients(app, "")
	if err == nil {
		registry.MainClients = toShowClientInfoList(mainClients)
	}

	lanes := cm.ListLanes(app)
	if len(lanes) > 0 {
		laneMap := make(map[string][]*ShowClientInfo)
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

func toShowClientInfoList(clients []*IceClientInfo) []*ShowClientInfo {
	if len(clients) == 0 {
		return []*ShowClientInfo{}
	}
	result := make([]*ShowClientInfo, len(clients))
	for i, c := range clients {
		result[i] = &ShowClientInfo{Address: c.Address}
	}
	return result
}

func (cm *ClientManager) GetAllLeafClasses(app int, lane string) map[int8][]*LeafNodeInfo {
	result := make(map[int8][]*LeafNodeInfo)
	types := []int8{5, 6, 7}
	for _, t := range types {
		clazzMap := cm.GetLeafTypeClasses(app, t, lane)
		if len(clazzMap) > 0 {
			var list []*LeafNodeInfo
			for clazz, info := range clazzMap {
				copied := copyNodeInfo(info)
				if copied != nil {
					copied.Clazz = clazz
					copied.Type = t
					if copied.Order == nil {
						copied.Order = IntPtr(100)
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

func (cm *ClientManager) GetClientLeafClasses(app int, address, lane string) map[int8][]*LeafNodeInfo {
	client, _ := cm.storage.GetClient(app, lane, address)
	if client == nil {
		client, _ = cm.storage.GetClient(app, "", address)
	}
	if client == nil || len(client.LeafNodes) == 0 {
		return nil
	}

	result := make(map[int8][]*LeafNodeInfo)
	for _, info := range client.LeafNodes {
		t := info.Type
		copied := copyNodeInfo(info)
		if copied != nil {
			if copied.Order == nil {
				copied.Order = IntPtr(100)
			}
			result[t] = append(result[t], copied)
		} else {
			result[t] = append(result[t], info)
		}
	}
	for _, list := range result {
		sort.Slice(list, func(i, j int) bool {
			return list[i].GetOrder() < list[j].GetOrder()
		})
	}
	return result
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
	clients, err := cm.storage.ListClients(app, lane)
	if err != nil {
		log.Printf("failed to list clients for app:%d lane:%s: %v", app, lane, err)
		return
	}
	if len(clients) == 0 {
		if lane != "" {
			cm.storage.DeleteEmptyLaneDir(app, lane)
		}
		return
	}

	timeoutMs := cm.config.ClientTimeout.Milliseconds()
	now := timeNowMs()

	var activeClients, inactiveClients []*IceClientInfo
	for _, c := range clients {
		if c.LastHeartbeat != nil && (now-*c.LastHeartbeat) < timeoutMs {
			activeClients = append(activeClients, c)
		} else {
			inactiveClients = append(inactiveClients, c)
		}
	}

	if len(inactiveClients) == 0 {
		return
	}

	cm.updateLatestClientIfNeeded(app, lane, activeClients, inactiveClients)

	deleteCount := 0
	for i, client := range inactiveClients {
		if len(activeClients) == 0 && i == len(inactiveClients)-1 {
			if lane != "" {
				cm.storage.DeleteClient(app, lane, client.Address)
				deleteCount++
			} else {
				log.Printf("preserved last inactive client for app:%d, address:%s", app, client.Address)
			}
			continue
		}
		cm.storage.DeleteClient(app, lane, client.Address)
		deleteCount++
	}

	if deleteCount > 0 {
		log.Printf("cleaned %d inactive clients for app:%d lane:%s", deleteCount, app, lane)
	}

	if lane != "" {
		cm.storage.DeleteEmptyLaneDir(app, lane)
	}
}

func (cm *ClientManager) updateLatestClientIfNeeded(app int, lane string, activeClients, inactiveClients []*IceClientInfo) {
	latestClient, _ := cm.storage.GetLatestClient(app, lane)

	needUpdate := false
	if latestClient == nil {
		needUpdate = true
	} else {
		for _, c := range inactiveClients {
			if latestClient.Address == c.Address {
				needUpdate = true
				break
			}
		}
	}

	if needUpdate {
		var newLatest *IceClientInfo
		for _, c := range activeClients {
			if len(c.LeafNodes) > 0 {
				newLatest = c
				break
			}
		}
		if newLatest != nil {
			cm.storage.UpdateLatestClient(app, lane, newLatest)
		} else if len(activeClients) > 0 {
			cm.storage.UpdateLatestClient(app, lane, activeClients[0])
		}
	}
}

func copyNodeInfo(source *LeafNodeInfo) *LeafNodeInfo {
	if source == nil {
		return nil
	}
	result := &LeafNodeInfo{
		Name:  source.Name,
		Clazz: source.Clazz,
		Type:  source.Type,
		Desc:  source.Desc,
		Order: source.Order,
	}
	if source.IceFields != nil {
		result.IceFields = make([]*IceFieldInfo, len(source.IceFields))
		for i, f := range source.IceFields {
			result.IceFields[i] = &IceFieldInfo{
				Field: f.Field,
				Type:  f.Type,
				Name:  f.Name,
				Desc:  f.Desc,
			}
		}
	}
	if source.HideFields != nil {
		result.HideFields = make([]*IceFieldInfo, len(source.HideFields))
		for i, f := range source.HideFields {
			result.HideFields[i] = &IceFieldInfo{
				Field: f.Field,
				Type:  f.Type,
				Name:  f.Name,
				Desc:  f.Desc,
			}
		}
	}
	return result
}

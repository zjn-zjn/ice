package service

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"strconv"
	"strings"
	"sync"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/storage"
)

type ServerService struct {
	storage       *storage.Storage
	clientManager *ClientManager
	config        *config.Config
	mu            sync.Mutex
}

func NewServerService(storage *storage.Storage, clientManager *ClientManager, config *config.Config) *ServerService {
	return &ServerService{
		storage:       storage,
		clientManager: clientManager,
		config:        config,
	}
}

func (s *ServerService) GetInitConfig(app int) (*model.IceTransferDto, error) {
	version, err := s.storage.GetVersion(app)
	if err != nil {
		return nil, err
	}
	bases, err := s.storage.ListActiveBases(app)
	if err != nil {
		return nil, err
	}
	confs, err := s.getActiveOnlineConfs(app)
	if err != nil {
		return nil, err
	}
	dtoBases := make([]*model.IceBase, len(bases))
	for i, b := range bases {
		dtoBases[i] = BaseToDtoWithName(b)
	}
	dtoConfs := make([]*model.IceConf, len(confs))
	for i, c := range confs {
		dtoConfs[i] = ConfToDtoWithName(c)
	}
	return &model.IceTransferDto{
		Version:             version,
		InsertOrUpdateBases: dtoBases,
		InsertOrUpdateConfs: dtoConfs,
	}, nil
}

func (s *ServerService) getActiveOnlineConfs(app int) ([]*model.IceConf, error) {
	all, err := s.storage.ListActiveConfs(app)
	if err != nil {
		return nil, err
	}
	var result []*model.IceConf
	for _, c := range all {
		if c.Status != nil && *c.Status == model.StatusOnline {
			result = append(result, c)
		}
	}
	return result, nil
}

func (s *ServerService) GetActiveConfById(app int, confId int64) *model.IceConf {
	conf, err := s.storage.GetConf(app, confId)
	if err != nil {
		slog.Error("active conf get failed", "confId", confId, "error", err)
		return nil
	}
	return conf
}

func (s *ServerService) GetUpdateConfById(app int, confId, iceId int64) *model.IceConf {
	conf, err := s.storage.GetConfUpdate(app, iceId, confId)
	if err != nil {
		slog.Error("update conf get failed", "confId", confId, "error", err)
		return nil
	}
	return conf
}

func (s *ServerService) GetMixConfById(app int, confId, iceId int64) *model.IceConf {
	updateConf := s.GetUpdateConfById(app, confId, iceId)
	if updateConf != nil {
		return updateConf
	}
	return s.GetActiveConfById(app, confId)
}

func (s *ServerService) GetMixConfListByIds(app int, confIds map[int64]bool, iceId int64) []*model.IceConf {
	if len(confIds) == 0 {
		return nil
	}
	result := make([]*model.IceConf, 0, len(confIds))
	for confId := range confIds {
		conf := s.GetMixConfById(app, confId, iceId)
		if conf == nil {
			return nil
		}
		result = append(result, conf)
	}
	return result
}

func (s *ServerService) GetActiveBaseById(app int, iceId int64) *model.IceBase {
	base, err := s.storage.GetBase(app, iceId)
	if err != nil {
		slog.Error("active base get failed", "iceId", iceId, "error", err)
		return nil
	}
	return base
}

func (s *ServerService) GetConfMixById(app int, confId, iceId int64, lane string) *model.IceShowNode {
	root := s.GetMixConfById(app, confId, iceId)
	return s.assembleShowNode(root, app, iceId, lane, false)
}

func (s *ServerService) GetConfActiveTreeById(app int, confId int64, lane string) *model.IceShowNode {
	root := s.GetActiveConfById(app, confId)
	return s.assembleShowNode(root, app, 0, lane, true)
}

func (s *ServerService) assembleShowNode(node *model.IceConf, app int, iceId int64, lane string, activeOnly bool) *model.IceShowNode {
	if node == nil {
		return nil
	}
	showNode := confToShow(node)

	if model.IsRelation(node.Type) {
		if showNode.SonIds != "" {
			sonIdStrs := strings.Split(showNode.SonIds, ",")
			var children []*model.IceShowNode
			for i, sonStr := range sonIdStrs {
				sonStr = strings.TrimSpace(sonStr)
				if sonStr == "" {
					continue
				}
				sonId, err := parseInt64(sonStr)
				if err != nil {
					continue
				}
				var child *model.IceConf
				if activeOnly {
					child = s.GetActiveConfById(app, sonId)
				} else {
					child = s.GetMixConfById(app, sonId, iceId)
				}
				if child != nil {
					showChild := s.assembleShowNode(child, app, iceId, lane, activeOnly)
					showChild.ParentId = model.Int64Ptr(node.GetMixId())
					idx := i
					showChild.Index = &idx
					children = append(children, showChild)
				}
			}
			showNode.Children = children
		}
	} else if model.IsLeaf(node.Type) && node.ConfName != "" {
		nodeInfo := s.clientManager.GetNodeInfo(node.App, "", node.ConfName, node.Type, lane)
		if nodeInfo != nil {
			showNode.ShowConf.HaveMeta = model.BoolPtr(true)
			showNode.ShowConf.NodeInfo = nodeInfo
			fillFieldValues(nodeInfo, node.ConfField)
		} else {
			showNode.ShowConf.HaveMeta = model.BoolPtr(false)
		}
	}

	if showNode.ForwardId != nil {
		var forwardConf *model.IceConf
		if activeOnly {
			forwardConf = s.GetActiveConfById(app, *showNode.ForwardId)
		} else {
			forwardConf = s.GetMixConfById(app, *showNode.ForwardId, iceId)
		}
		forwardNode := s.assembleShowNode(forwardConf, app, iceId, lane, activeOnly)
		if forwardNode != nil {
			forwardNode.NextId = model.Int64Ptr(node.GetMixId())
			showNode.Forward = forwardNode
		}
	}
	return showNode
}

func fillFieldValues(nodeInfo *model.LeafNodeInfo, confField string) {
	if confField == "" || confField == "{}" {
		return
	}
	var fieldValues map[string]any
	if err := json.Unmarshal([]byte(confField), &fieldValues); err != nil {
		slog.Error("confField parse failed", "confField", confField, "error", err)
		return
	}
	if len(fieldValues) == 0 {
		return
	}

	fillFields := func(fields []*model.IceFieldInfo) {
		for _, f := range fields {
			if val, ok := fieldValues[f.Field]; ok {
				f.Value = val
				isNull := val == nil
				f.ValueNull = &isNull
			}
		}
	}
	fillFields(nodeInfo.IceFields)
	fillFields(nodeInfo.HideFields)
}

func (s *ServerService) UpdateLocalConfUpdateCache(conf *model.IceConf) error {
	EnsureConfDefaults(conf)
	return s.storage.SaveConfUpdate(conf.App, *conf.IceId, conf)
}

func (s *ServerService) UpdateLocalConfActiveCache(conf *model.IceConf) error {
	activeCopy := *conf
	activeCopy.IceId = nil
	activeCopy.ConfId = nil
	EnsureConfDefaults(&activeCopy)
	return s.storage.SaveConf(&activeCopy)
}

func EnsureConfDefaults(conf *model.IceConf) {
	if conf.Status == nil {
		conf.Status = model.Int8Ptr(model.StatusOnline)
	}
	if conf.TimeType == nil {
		conf.TimeType = model.Int8Ptr(model.TimeTypeNone)
	}
	if conf.UpdateAt == nil {
		now := model.TimeNowMs()
		conf.UpdateAt = &now
	}
}

func (s *ServerService) UpdateLocalBaseCache(base *model.IceBase) error {
	return s.storage.SaveBase(base)
}

// HaveCircle checks if linking linkId to nodeId would create a cycle
func (s *ServerService) HaveCircle(app int, iceId int64, nodeId, linkId int64) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	if nodeId == linkId {
		return true
	}
	visited := make(map[int64]bool)
	return s.checkCircle(app, iceId, nodeId, linkId, visited)
}

func (s *ServerService) HaveCircleMulti(app int, iceId int64, nodeId int64, linkIds []int64) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	for _, linkId := range linkIds {
		if nodeId == linkId {
			return true
		}
		visited := make(map[int64]bool)
		if s.checkCircle(app, iceId, nodeId, linkId, visited) {
			return true
		}
	}
	return false
}

func (s *ServerService) HaveCircleSet(app int, iceId int64, nodeId int64, linkIds map[int64]bool) bool {
	s.mu.Lock()
	defer s.mu.Unlock()

	for linkId := range linkIds {
		if nodeId == linkId {
			return true
		}
		visited := make(map[int64]bool)
		if s.checkCircle(app, iceId, nodeId, linkId, visited) {
			return true
		}
	}
	return false
}

func (s *ServerService) checkCircle(app int, iceId int64, nodeId, linkId int64, visited map[int64]bool) bool {
	if visited[linkId] {
		return false
	}
	visited[linkId] = true

	conf := s.GetMixConfById(app, linkId, iceId)
	if conf == nil {
		return false
	}

	sonIds := conf.GetSonLongIds()
	for _, sonId := range sonIds {
		if sonId == nodeId {
			return true
		}
		if s.checkCircle(app, iceId, nodeId, sonId, visited) {
			return true
		}
	}

	if conf.ForwardId != nil {
		if *conf.ForwardId == nodeId {
			return true
		}
		if s.checkCircle(app, iceId, nodeId, *conf.ForwardId, visited) {
			return true
		}
	}
	return false
}

func (s *ServerService) GetVersion(app int) (int64, error) {
	return s.storage.GetVersion(app)
}

func (s *ServerService) GetAndIncrementVersion(app int) (int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	current, err := s.storage.GetVersion(app)
	if err != nil {
		return 0, err
	}
	next := current + 1
	if err := s.storage.SetVersion(app, next); err != nil {
		return 0, err
	}
	return next, nil
}

func (s *ServerService) Release(app int, iceId int64) (*model.IceTransferDto, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	confUpdates, err := s.storage.ListConfUpdates(app, iceId)
	if err != nil {
		return nil, err
	}
	if len(confUpdates) == 0 {
		return nil, nil
	}

	transferDto := &model.IceTransferDto{}
	var releasedConfs []*model.IceConf
	now := model.TimeNowMs()

	for _, confUpdate := range confUpdates {
		var confId int64
		if confUpdate.ConfId != nil {
			confId = *confUpdate.ConfId
		} else {
			confId = confUpdate.ID
		}

		oldConf, _ := s.storage.GetConf(app, confId)

		newConf := &model.IceConf{
			ID:         confId,
			App:        confUpdate.App,
			Name:       confUpdate.Name,
			SonIds:     confUpdate.SonIds,
			Type:       confUpdate.Type,
			Inverse:    confUpdate.Inverse,
			ConfName:   confUpdate.ConfName,
			ConfField:  confUpdate.ConfField,
			ForwardId:  confUpdate.ForwardId,
			Start:      confUpdate.Start,
			End:        confUpdate.End,
			ErrorState: confUpdate.ErrorState,
			UpdateAt:   &now,
		}

		// Status with default
		if confUpdate.Status != nil {
			newConf.Status = confUpdate.Status
		} else {
			newConf.Status = model.Int8Ptr(model.StatusOnline)
		}
		// TimeType with default
		if confUpdate.TimeType != nil {
			newConf.TimeType = confUpdate.TimeType
		} else {
			newConf.TimeType = model.Int8Ptr(model.TimeTypeNone)
		}
		// Preserve createAt
		if oldConf != nil && oldConf.CreateAt != nil {
			newConf.CreateAt = oldConf.CreateAt
		} else if confUpdate.CreateAt != nil {
			newConf.CreateAt = confUpdate.CreateAt
		} else {
			newConf.CreateAt = &now
		}

		if err := s.storage.SaveConf(newConf); err != nil {
			return nil, err
		}
		releasedConfs = append(releasedConfs, newConf)
		if err := s.storage.DeleteConfUpdate(app, iceId, confId); err != nil {
			return nil, err
		}
	}

	// Increment version
	current, err := s.storage.GetVersion(app)
	if err != nil {
		return nil, err
	}
	newVersion := current + 1
	if err := s.storage.SetVersion(app, newVersion); err != nil {
		return nil, err
	}

	transferDto.Version = newVersion
	transferDto.InsertOrUpdateConfs = releasedConfs

	if err := s.storage.SaveVersionUpdate(app, newVersion, transferDto); err != nil {
		return nil, err
	}
	s.storage.CleanOldVersions(app, s.config.VersionRetention)

	return transferDto, nil
}

func (s *ServerService) UpdateClean(app int, iceId int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.storage.DeleteAllConfUpdates(app, iceId)
}

func (s *ServerService) GetChanges(app int, iceId int64, confId *int64) ([]*model.ConfChangeItem, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	if confId != nil {
		update, err := s.storage.GetConfUpdate(app, iceId, *confId)
		if err != nil || update == nil {
			return nil, err
		}
		active, _ := s.storage.GetConf(app, *confId)
		return []*model.ConfChangeItem{{ConfId: *confId, Active: active, Update: update}}, nil
	}

	updates, err := s.storage.ListConfUpdates(app, iceId)
	if err != nil {
		return nil, err
	}
	items := make([]*model.ConfChangeItem, 0, len(updates))
	for _, u := range updates {
		cid := u.GetMixId()
		active, _ := s.storage.GetConf(app, cid)
		items = append(items, &model.ConfChangeItem{ConfId: cid, Active: active, Update: u})
	}
	return items, nil
}

func (s *ServerService) GetAllUpdateConfList(app int, iceId int64) []*model.IceConf {
	s.mu.Lock()
	defer s.mu.Unlock()

	confs, err := s.storage.ListConfUpdates(app, iceId)
	if err != nil {
		slog.Error("update conf list failed", "app", app, "iceId", iceId, "error", err)
		return nil
	}
	return confs
}

func (s *ServerService) GetAllActiveConfSet(app int, rootId int64) []*model.IceConf {
	s.mu.Lock()
	defer s.mu.Unlock()
	var result []*model.IceConf
	visited := make(map[int64]bool)
	s.assembleActiveConf(app, &result, rootId, visited)
	return result
}

func (s *ServerService) assembleActiveConf(app int, confList *[]*model.IceConf, nodeId int64, visited map[int64]bool) {
	if visited[nodeId] {
		return
	}
	visited[nodeId] = true

	conf := s.GetActiveConfById(app, nodeId)
	if conf == nil {
		return
	}
	*confList = append(*confList, conf)

	if model.IsRelation(conf.Type) {
		for _, sonId := range conf.GetSonLongIds() {
			s.assembleActiveConf(app, confList, sonId, visited)
		}
	}
	if conf.ForwardId != nil {
		s.assembleActiveConf(app, confList, *conf.ForwardId, visited)
	}
}

func (s *ServerService) Recycle(recycleApp *int) {
	slog.Info("recycle started")
	start := model.TimeNowMs()

	if recycleApp != nil {
		s.recycleByApp(*recycleApp)
	} else {
		apps, err := s.storage.ListApps()
		if err != nil {
			slog.Error("recycle failed", "error", err)
			return
		}
		for _, app := range apps {
			if app.ID != nil {
				s.recycleByApp(*app.ID)
			}
		}
	}
	slog.Info("recycle completed", "durationMs", model.TimeNowMs()-start)
}

func (s *ServerService) recycleByApp(app int) {
	reachableIds, err := s.getReachableIds(app)
	if err != nil {
		slog.Error("reachable ids failed", "app", app, "error", err)
		return
	}

	protectThreshold := model.TimeNowMs() - int64(s.config.RecycleProtectDays)*24*60*60*1000
	hard := s.config.RecycleWay != "soft"

	allConfs, err := s.storage.ListConfs(app)
	if err != nil {
		slog.Error("confs list failed", "app", app, "error", err)
	} else {
		for _, conf := range allConfs {
			if conf.Status != nil && *conf.Status != model.StatusDeleted && !reachableIds[conf.ID] {
				if conf.UpdateAt != nil && *conf.UpdateAt > protectThreshold {
					continue
				}
				if err := s.storage.DeleteConf(app, conf.ID, hard); err != nil {
					slog.Error("conf delete failed", "confId", conf.ID, "app", app, "error", err)
				} else {
					slog.Info("unreachable conf recycled", "confId", conf.ID, "app", app)
				}
			}
		}
	}

	allBases, err := s.storage.ListBases(app)
	if err != nil {
		slog.Error("bases list failed", "app", app, "error", err)
	} else {
		for _, base := range allBases {
			if base.ID == nil || base.Status == nil {
				continue
			}
			if *base.Status == model.StatusOffline {
				if base.UpdateAt != nil && *base.UpdateAt > protectThreshold {
					continue
				}
				if err := s.storage.DeleteBase(app, *base.ID, hard); err != nil {
					slog.Error("base delete failed", "baseId", *base.ID, "app", app, "error", err)
				} else {
					slog.Info("offline base recycled", "baseId", *base.ID, "app", app)
				}
			}
		}
	}
}

func (s *ServerService) getReachableIds(app int) (map[int64]bool, error) {
	reachableIds := make(map[int64]bool)
	visited := make(map[int64]bool)

	// Collect from active bases
	bases, err := s.storage.ListActiveBases(app)
	if err != nil {
		return nil, err
	}
	for _, base := range bases {
		if base.ConfID != nil {
			s.assembleReachableIdsFromConfs(app, reachableIds, *base.ConfID, visited)
		}
	}

	// Collect from updates
	if err := s.assembleReachableIdsFromUpdates(app, reachableIds); err != nil {
		return nil, err
	}

	return reachableIds, nil
}

func (s *ServerService) assembleReachableIdsFromConfs(app int, reachableIds map[int64]bool, confId int64, visited map[int64]bool) {
	if visited[confId] {
		return
	}
	visited[confId] = true
	reachableIds[confId] = true

	conf := s.GetActiveConfById(app, confId)
	if conf != nil {
		for _, sonId := range conf.GetSonLongIds() {
			s.assembleReachableIdsFromConfs(app, reachableIds, sonId, visited)
		}
		if conf.ForwardId != nil {
			s.assembleReachableIdsFromConfs(app, reachableIds, *conf.ForwardId, visited)
		}
	}
}

func (s *ServerService) assembleReachableIdsFromUpdates(app int, reachableIds map[int64]bool) error {
	allBases, err := s.storage.ListBases(app)
	if err != nil {
		return err
	}
	for _, base := range allBases {
		if base.ID == nil {
			continue
		}
		updates, err := s.storage.ListConfUpdates(app, *base.ID)
		if err != nil {
			continue
		}
		for _, update := range updates {
			if update.ConfId != nil {
				reachableIds[*update.ConfId] = true
			}
			if update.SonIds != "" {
				for _, sonStr := range strings.Split(update.SonIds, ",") {
					sonStr = strings.TrimSpace(sonStr)
					if sonStr != "" {
						id, err := parseInt64(sonStr)
						if err == nil && id > 0 {
							reachableIds[id] = true
						}
					}
				}
			}
			if update.ForwardId != nil {
				reachableIds[*update.ForwardId] = true
			}
		}
	}
	return nil
}

// confToShow converts an IceConf to an IceShowNode (matching ServerConstant.confToShow)
func confToShow(conf *model.IceConf) *model.IceShowNode {
	show := &model.IceShowNode{}
	showConf := &model.NodeShowConf{}
	show.ShowConf = showConf

	show.ForwardId = conf.ForwardId
	showConf.NodeId = conf.GetMixId()
	showConf.ErrorState = conf.ErrorState

	show.Start = conf.Start
	show.End = conf.End
	if conf.TimeType != nil && *conf.TimeType != model.TimeTypeNone {
		show.TimeType = conf.TimeType
	}

	if model.IsRelation(conf.Type) {
		if conf.SonIds != "" {
			show.SonIds = conf.SonIds
		}
		name := fmt.Sprintf("%d-%s", conf.GetMixId(), model.NodeTypeName(conf.Type))
		if conf.Name != "" {
			name += "-" + conf.Name
		}
		showConf.LabelName = name
	} else {
		showConf.ConfName = conf.ConfName
		showConf.ConfField = conf.ConfField
		shortName := conf.ConfName
		if idx := strings.LastIndex(shortName, "."); idx >= 0 {
			shortName = shortName[idx+1:]
		}
		if shortName == "" {
			shortName = " "
		}
		name := fmt.Sprintf("%d-%s", conf.GetMixId(), shortName)
		if conf.Name != "" {
			name += "-" + conf.Name
		}
		showConf.LabelName = name
	}

	showConf.Updating = model.BoolPtr(conf.IsUpdating())
	showConf.Inverse = model.BoolPtr(conf.IsInverse())
	showConf.NodeName = conf.Name
	showConf.NodeType = &conf.Type

	return show
}

// ConfToDtoWithName creates a DTO optimized for push data (matching ServerConstant.confToDtoWithName)
func ConfToDtoWithName(conf *model.IceConf) *model.IceConf {
	dto := &model.IceConf{
		ID:         conf.ID,
		Type:       conf.Type,
		ForwardId:  conf.ForwardId,
		ErrorState: conf.ErrorState,
		Start:      conf.Start,
		End:        conf.End,
		ConfId:     conf.ConfId,
		IceId:      conf.IceId,
	}
	if conf.TimeType != nil && *conf.TimeType != model.TimeTypeNone {
		dto.TimeType = conf.TimeType
	}
	if model.IsLeaf(conf.Type) {
		dto.ConfName = conf.ConfName
		if conf.ConfField != "" && conf.ConfField != "{}" {
			dto.ConfField = conf.ConfField
		}
	} else if conf.SonIds != "" {
		dto.SonIds = conf.SonIds
	}
	if conf.Inverse != nil && *conf.Inverse {
		dto.Inverse = conf.Inverse
	}
	if conf.Name != "" {
		dto.Name = conf.Name
	}
	return dto
}

// BaseToDtoWithName creates a DTO for push data (matching ServerConstant.baseToDtoWithName)
func BaseToDtoWithName(base *model.IceBase) *model.IceBase {
	dto := &model.IceBase{
		ID:     base.ID,
		ConfID: base.ConfID,
	}
	if base.Debug != nil && *base.Debug != 0 {
		dto.Debug = base.Debug
	}
	dto.Start = base.Start
	dto.End = base.End
	if base.TimeType != nil && *base.TimeType != model.TimeTypeNone {
		dto.TimeType = base.TimeType
	}
	if base.Scenes != "" {
		dto.Scenes = base.Scenes
	}
	if base.Name != "" {
		dto.Name = base.Name
	}
	return dto
}

// GetMockSchema traverses the rule tree and collects read roamKeys from all leaf nodes.
// If address is specified, checks if that client is active. If not, falls back to lane → trunk
// and sets fallback=true in the response.
func (s *ServerService) GetMockSchema(app int, iceId, confId int64, lane, address string) *model.MockSchemaResponse {
	var rootConfId int64
	if confId > 0 {
		rootConfId = confId
	} else if iceId > 0 {
		base := s.GetActiveBaseById(app, iceId)
		if base == nil || base.ConfID == nil {
			return nil
		}
		rootConfId = *base.ConfID
	}

	// Check if the requested address is active; if not, fallback
	fallback := false
	effectiveAddress := address
	if address != "" {
		if !s.clientManager.IsClientActive(app, lane, address) && !s.clientManager.IsClientActive(app, "", address) {
			// Address is offline, fallback
			fallback = true
			effectiveAddress = ""
		}
	}

	seen := make(map[string]bool)
	var fields []*model.MockSchemaField
	s.collectMockSchema(app, rootConfId, iceId, lane, effectiveAddress, seen, &fields, make(map[int64]bool))
	return &model.MockSchemaResponse{Fields: fields, Fallback: fallback}
}

func (s *ServerService) collectMockSchema(app int, confId, iceId int64, lane, address string, seen map[string]bool, fields *[]*model.MockSchemaField, visited map[int64]bool) {
	if visited[confId] {
		return
	}
	visited[confId] = true

	conf := s.GetActiveConfById(app, confId)
	if conf == nil {
		return
	}

	if model.IsRelation(conf.Type) {
		for _, sonId := range conf.GetSonLongIds() {
			s.collectMockSchema(app, sonId, iceId, lane, address, seen, fields, visited)
		}
	} else if model.IsLeaf(conf.Type) && conf.ConfName != "" {
		nodeInfo := s.clientManager.GetNodeInfo(conf.App, address, conf.ConfName, conf.Type, lane)
		if nodeInfo != nil {
			var fieldValues map[string]any
			if conf.ConfField != "" && conf.ConfField != "{}" {
				_ = json.Unmarshal([]byte(conf.ConfField), &fieldValues)
			}
			for _, rk := range nodeInfo.RoamKeys {
				if rk.Direction != "read" && rk.Direction != "read_write" {
					continue
				}
				key, dynamic := resolveKeyParts(rk.KeyParts, fieldValues)
				if key == "" {
					continue
				}
				if seen[key] {
					continue
				}
				seen[key] = true
				*fields = append(*fields, &model.MockSchemaField{
					Key:      key,
					NodeId:   conf.GetMixId(),
					NodeName: conf.Name,
					Dynamic:  dynamic,
				})
			}
		}
	}

	if conf.ForwardId != nil {
		s.collectMockSchema(app, *conf.ForwardId, iceId, lane, address, seen, fields, visited)
	}
}

// resolveKeyParts resolves key parts to a string key.
// Returns (key, dynamic). For dynamic keys, returns the literal prefix with a trailing "*".
func resolveKeyParts(parts []*model.KeyPart, fieldValues map[string]any) (string, bool) {
	if len(parts) == 0 {
		return "", false
	}
	var sb strings.Builder
	for _, p := range parts {
		switch p.Type {
		case "literal":
			sb.WriteString(p.Value)
		case "field":
			// field type: ref points to an iceField name, resolve from confField values
			// Try exact match first, then case-insensitive (Go exports as "Key", JSON stores as "key")
			if p.Ref != "" && fieldValues != nil {
				if v, ok := fieldValues[p.Ref]; ok {
					sb.WriteString(fmt.Sprintf("%v", v))
					continue
				}
				lower := strings.ToLower(p.Ref[:1]) + p.Ref[1:]
				if v, ok := fieldValues[lower]; ok {
					sb.WriteString(fmt.Sprintf("%v", v))
					continue
				}
			}
			// no configured value, use field ref name as placeholder and mark dynamic
			if p.Ref != "" {
				sb.WriteString("${" + p.Ref + "}")
			}
			return sb.String(), true
		case "ref", "dynamic":
			if sb.Len() > 0 {
				sb.WriteString("*")
				return sb.String(), true
			}
			return "*", true
		case "concat":
			key, dyn := resolveKeyParts(p.Parts, fieldValues)
			if key == "" {
				return "", false
			}
			sb.WriteString(key)
			if dyn {
				return sb.String(), true
			}
		default:
			return "", false
		}
	}
	return sb.String(), false
}

func parseInt64(s string) (int64, error) {
	return strconv.ParseInt(strings.TrimSpace(s), 10, 64)
}

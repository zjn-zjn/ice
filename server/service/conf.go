package service

import (
	"bytes"
	"encoding/json"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"sync"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/storage"
)

type ConfService struct {
	config        *config.Config
	storage       *storage.Storage
	serverService *ServerService
	clientManager *ClientManager
	mu            sync.Mutex
}

func NewConfService(cfg *config.Config, storage *storage.Storage, serverService *ServerService, clientManager *ClientManager) *ConfService {
	return &ConfService{config: cfg, storage: storage, serverService: serverService, clientManager: clientManager}
}

func (cs *ConfService) ConfEdit(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	if err := cs.paramHandle(editNode); err != nil {
		return nil, err
	}
	if cs.config.Mode == "controlled" {
		if *editNode.EditType == model.EditTypeAddSon && editNode.MultiplexIds == "" {
			return nil, model.ControlledModeError("新增节点")
		}
		if *editNode.EditType == model.EditTypeAddForward && editNode.MultiplexIds == "" {
			return nil, model.ControlledModeError("新增前置节点")
		}
	}
	cs.mu.Lock()
	defer cs.mu.Unlock()
	switch *editNode.EditType {
	case model.EditTypeAddSon:
		return cs.addSon(editNode)
	case model.EditTypeEdit:
		return cs.edit(editNode)
	case model.EditTypeDelete:
		return cs.deleteNode(editNode)
	case model.EditTypeAddForward:
		return cs.addForward(editNode)
	case model.EditTypeExchange:
		return cs.exchange(editNode)
	case model.EditTypeMove:
		return cs.moveNode(editNode)
	}
	return nil, model.InputError("unknown editType")
}

func (cs *ConfService) addSon(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	app := *editNode.App
	iceId := *editNode.IceId

	operateConf := cs.serverService.GetMixConfById(app, *editNode.SelectId, iceId)
	if operateConf == nil {
		return nil, model.IDNotExist("selectId", *editNode.SelectId)
	}
	if model.IsLeaf(operateConf.Type) {
		return nil, model.InputError(fmt.Sprintf("only relation can have son id:%d", *editNode.SelectId))
	}

	if editNode.MultiplexIds != "" {
		sonIdStrs := strings.Split(editNode.MultiplexIds, ",")
		sonIdSet := make(map[int64]bool, len(sonIdStrs))
		sonIdList := make([]int64, 0, len(sonIdStrs))
		for _, s := range sonIdStrs {
			id, _ := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
			sonIdSet[id] = true
			sonIdList = append(sonIdList, id)
		}
		if cs.serverService.HaveCircleMulti(app, iceId, operateConf.GetMixId(), sonIdList) {
			return nil, model.InputError("存在循环引用，请检查 sonIds")
		}
		children := cs.serverService.GetMixConfListByIds(app, sonIdSet, iceId)
		if children == nil || len(children) != len(sonIdSet) {
			return nil, model.InputError("one of son id not exist:" + editNode.MultiplexIds)
		}
		if operateConf.SonIds == "" {
			operateConf.SonIds = editNode.MultiplexIds
		} else {
			operateConf.SonIds = operateConf.SonIds + "," + editNode.MultiplexIds
		}
		if err := cs.updateConf(operateConf, iceId); err != nil {
			return nil, err
		}
		nodes := cs.assembleMultiplexNodes(app, iceId, editNode.Lane, sonIdList)
		return &model.EditConfResponse{NodeId: operateConf.GetMixId(), Nodes: nodes}, nil
	}

	createConf, err := cs.createNewConf(editNode, app)
	if err != nil {
		return nil, err
	}
	if operateConf.SonIds == "" {
		operateConf.SonIds = strconv.FormatInt(createConf.GetMixId(), 10)
	} else {
		operateConf.SonIds = operateConf.SonIds + "," + strconv.FormatInt(createConf.GetMixId(), 10)
	}
	createConf.IceId = &iceId
	createConf.ConfId = &createConf.ID
	if err := cs.saveConfUpdate(createConf, iceId); err != nil {
		return nil, err
	}
	if err := cs.updateConf(operateConf, iceId); err != nil {
		return nil, err
	}
	return &model.EditConfResponse{NodeId: createConf.GetMixId()}, nil
}

func (cs *ConfService) edit(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	app := *editNode.App
	iceId := *editNode.IceId

	operateConf := cs.serverService.GetMixConfById(app, *editNode.SelectId, iceId)
	if operateConf == nil {
		return nil, model.IDNotExist("selectId", *editNode.SelectId)
	}

	operateConf.TimeType = editNode.TimeType
	operateConf.Start = editNode.Start
	operateConf.End = editNode.End
	inverse := editNode.IsInverse()
	operateConf.Inverse = &inverse
	if editNode.Name != "" {
		operateConf.Name = editNode.Name
	} else {
		operateConf.Name = ""
	}

	if model.IsLeaf(operateConf.Type) {
		leafNodeInfo := cs.leafClassCheck(app, operateConf.ConfName, operateConf.Type)
		if editNode.ConfField != "" {
			if checkRes := checkIllegalAndAdjustJson(editNode, leafNodeInfo); checkRes != "" {
				return nil, model.ConfigFieldIllegal(checkRes)
			}
		}
		operateConf.ConfField = editNode.ConfField
	}
	if err := cs.updateConf(operateConf, iceId); err != nil {
		return nil, err
	}
	return &model.EditConfResponse{NodeId: operateConf.GetMixId()}, nil
}

func (cs *ConfService) deleteNode(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	app := *editNode.App
	iceId := *editNode.IceId

	if editNode.ParentId != nil {
		operateConf := cs.serverService.GetMixConfById(app, *editNode.ParentId, iceId)
		if operateConf == nil {
			return nil, model.IDNotExist("parentId", *editNode.ParentId)
		}
		if operateConf.SonIds == "" {
			return nil, model.InputError("父节点无子节点")
		}
		sonIdStrs := strings.Split(operateConf.SonIds, ",")
		index := -1
		if editNode.Index != nil {
			index = *editNode.Index
		}
		if index < 0 || index >= len(sonIdStrs) || sonIdStrs[index] != strconv.FormatInt(*editNode.SelectId, 10) {
			return nil, model.InputError("父节点在指定位置无此子节点")
		}
		// Remove the son at index
		var sb strings.Builder
		for i, s := range sonIdStrs {
			if i != index {
				if sb.Len() > 0 {
					sb.WriteByte(',')
				}
				sb.WriteString(s)
			}
		}
		operateConf.SonIds = sb.String()
		if err := cs.updateConf(operateConf, iceId); err != nil {
			return nil, err
		}
		return &model.EditConfResponse{NodeId: operateConf.GetMixId()}, nil
	}

	if editNode.NextId != nil {
		operateConf := cs.serverService.GetMixConfById(app, *editNode.NextId, iceId)
		if operateConf == nil {
			return nil, model.IDNotExist("nextId", *editNode.NextId)
		}
		if operateConf.ForwardId == nil || *operateConf.ForwardId != *editNode.SelectId {
			return nil, model.InputError(fmt.Sprintf("nextId:%d not have this forward:%d", *editNode.NextId, *editNode.SelectId))
		}
		operateConf.ForwardId = nil
		if err := cs.updateConf(operateConf, iceId); err != nil {
			return nil, err
		}
		return &model.EditConfResponse{NodeId: operateConf.GetMixId()}, nil
	}
	return nil, model.InputError("根节点不支持删除")
}

func (cs *ConfService) addForward(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	app := *editNode.App
	iceId := *editNode.IceId

	operateConf := cs.serverService.GetMixConfById(app, *editNode.SelectId, iceId)
	if operateConf == nil {
		return nil, model.IDNotExist("selectId", *editNode.SelectId)
	}
	if operateConf.ForwardId != nil {
		return nil, model.AlreadyExist("forward")
	}

	if editNode.MultiplexIds != "" {
		forwardId, _ := strconv.ParseInt(editNode.MultiplexIds, 10, 64)
		if cs.serverService.HaveCircle(app, iceId, operateConf.GetMixId(), forwardId) {
			return nil, model.InputError("存在循环引用，请检查 forwardIds")
		}
		forward := cs.serverService.GetMixConfById(app, forwardId, iceId)
		if forward == nil {
			return nil, model.IDNotExist("forwardId", forwardId)
		}
		operateConf.ForwardId = &forwardId
		if err := cs.updateConf(operateConf, iceId); err != nil {
			return nil, err
		}
		nodes := cs.assembleMultiplexNodes(app, iceId, editNode.Lane, []int64{forwardId})
		return &model.EditConfResponse{NodeId: operateConf.GetMixId(), Nodes: nodes}, nil
	}

	createConf, err := cs.createNewConf(editNode, app)
	if err != nil {
		return nil, err
	}
	fwdId := createConf.GetMixId()
	operateConf.ForwardId = &fwdId
	createConf.IceId = &iceId
	createConf.ConfId = &createConf.ID
	if err := cs.saveConfUpdate(createConf, iceId); err != nil {
		return nil, err
	}
	if err := cs.updateConf(operateConf, iceId); err != nil {
		return nil, err
	}
	return &model.EditConfResponse{NodeId: createConf.GetMixId()}, nil
}

func (cs *ConfService) exchange(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	app := *editNode.App
	iceId := *editNode.IceId

	if editNode.MultiplexIds != "" {
		if editNode.ParentId == nil && editNode.NextId == nil {
			return nil, model.InputError("根节点不支持通过 ID 替换")
		}
		if editNode.ParentId != nil {
			conf := cs.serverService.GetMixConfById(app, *editNode.ParentId, iceId)
			if conf == nil {
				return nil, model.IDNotExist("parentId", *editNode.ParentId)
			}
			sonIdStrs := strings.Split(editNode.MultiplexIds, ",")
			sonIdSet := make(map[int64]bool, len(sonIdStrs))
			sonIdList := make([]int64, 0, len(sonIdStrs))
			for _, s := range sonIdStrs {
				id, _ := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
				sonIdSet[id] = true
				sonIdList = append(sonIdList, id)
			}
			children := cs.serverService.GetMixConfListByIds(app, sonIdSet, iceId)
			if children == nil || len(children) != len(sonIdSet) {
				return nil, model.IDNotExist("one of sonId", editNode.MultiplexIds)
			}
			if cs.serverService.HaveCircleSet(app, iceId, *editNode.ParentId, sonIdSet) {
				return nil, model.InputError("存在循环引用，请检查 sonIds")
			}
			sonIds := strings.Split(conf.SonIds, ",")
			index := -1
			if editNode.Index != nil {
				index = *editNode.Index
			}
			if index < 0 || index >= len(sonIds) || sonIds[index] != strconv.FormatInt(*editNode.SelectId, 10) {
				return nil, model.InputError("父节点在指定位置无此子节点")
			}
			sonIds[index] = editNode.MultiplexIds
			conf.SonIds = strings.Join(sonIds, ",")
			if err := cs.updateConf(conf, iceId); err != nil {
				return nil, err
			}
			nodes := cs.assembleMultiplexNodes(app, iceId, editNode.Lane, sonIdList)
			return &model.EditConfResponse{NodeId: conf.GetMixId(), Nodes: nodes}, nil
		}
		if editNode.NextId != nil {
			conf := cs.serverService.GetMixConfById(app, *editNode.NextId, iceId)
			if conf == nil {
				return nil, model.IDNotExist("nextId", *editNode.NextId)
			}
			if conf.ForwardId == nil {
				return nil, model.InputError(fmt.Sprintf("nextId:%d no forward", *editNode.NextId))
			}
			exchangeForwardId, _ := strconv.ParseInt(editNode.MultiplexIds, 10, 64)
			if cs.serverService.HaveCircle(app, iceId, *editNode.NextId, exchangeForwardId) {
				return nil, model.InputError("存在循环引用，请检查 forwardId")
			}
			conf.ForwardId = &exchangeForwardId
			if err := cs.updateConf(conf, iceId); err != nil {
				return nil, err
			}
			nodes := cs.assembleMultiplexNodes(app, iceId, editNode.Lane, []int64{exchangeForwardId})
			return &model.EditConfResponse{NodeId: conf.GetMixId(), Nodes: nodes}, nil
		}
	}

	operateConf := cs.serverService.GetMixConfById(app, *editNode.SelectId, iceId)
	if operateConf == nil {
		return nil, model.IDNotExist("selectId", *editNode.SelectId)
	}

	inverse := editNode.IsInverse()
	operateConf.Inverse = &inverse
	operateConf.TimeType = editNode.TimeType
	operateConf.Start = editNode.Start
	operateConf.End = editNode.End

	if editNode.NodeType != nil {
		if model.IsRelation(operateConf.Type) && !model.IsRelation(*editNode.NodeType) {
			operateConf.SonIds = ""
		}
		operateConf.Type = *editNode.NodeType
	}
	operateConf.App = app
	if editNode.Name != "" {
		operateConf.Name = editNode.Name
	}

	if editNode.NodeType != nil && model.IsLeaf(*editNode.NodeType) {
		leafNodeInfo := cs.leafClassCheck(app, editNode.ConfName, *editNode.NodeType)
		if editNode.ConfField != "" {
			if checkRes := checkIllegalAndAdjustJson(editNode, leafNodeInfo); checkRes != "" {
				return nil, model.ConfigFieldIllegal(checkRes)
			}
		}
		operateConf.ConfName = editNode.ConfName
		operateConf.ConfField = editNode.ConfField
	}
	if err := cs.updateConf(operateConf, iceId); err != nil {
		return nil, err
	}
	return &model.EditConfResponse{NodeId: operateConf.GetMixId()}, nil
}

func (cs *ConfService) moveNode(editNode *model.IceEditNode) (*model.EditConfResponse, error) {
	app := *editNode.App
	iceId := *editNode.IceId

	if editNode.ParentId != nil && editNode.Index != nil {
		parent := cs.serverService.GetMixConfById(app, *editNode.ParentId, iceId)
		if parent == nil {
			return nil, model.IDNotExist("parentId", *editNode.ParentId)
		}
		if parent.SonIds == "" {
			return nil, model.InputError("父节点无子节点")
		}
		if model.IsLeaf(parent.Type) {
			return nil, model.InputError("该节点不是父节点")
		}
		sonIds := strings.Split(parent.SonIds, ",")
		index := *editNode.Index
		selectIdStr := strconv.FormatInt(*editNode.SelectId, 10)
		if index < 0 || index >= len(sonIds) || sonIds[index] != selectIdStr {
			return nil, model.InputError("父节点在指定位置无此子节点")
		}

		// Move to forward of another node
		if editNode.MoveToNextId != nil {
			moveToNext := cs.serverService.GetMixConfById(app, *editNode.MoveToNextId, iceId)
			if moveToNext == nil {
				return nil, model.IDNotExist("moveToNextId", *editNode.MoveToNextId)
			}
			if moveToNext.ForwardId != nil {
				return nil, model.CustomError(fmt.Sprintf("move to moveToNext:%d already has forward", *editNode.MoveToNextId))
			}
			if cs.serverService.HaveCircle(app, iceId, moveToNext.GetMixId(), *editNode.SelectId) {
				return nil, model.InputError("无法移动，存在循环引用")
			}
			moveToNext.ForwardId = editNode.SelectId
			// Remove from parent
			var parts []string
			for i, s := range sonIds {
				if i != index {
					parts = append(parts, s)
				}
			}
			parent.SonIds = strings.Join(parts, ",")
			if err := cs.updateConf(moveToNext, iceId); err != nil {
				return nil, err
			}
			if err := cs.updateConf(parent, iceId); err != nil {
				return nil, err
			}
			return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
		}

		// Move within same parent or to different parent
		moveToParentId := editNode.MoveToParentId
		if moveToParentId == nil || *moveToParentId == *editNode.ParentId {
			// Reorder within same parent
			if len(sonIds) == 1 {
				return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
			}
			if editNode.MoveTo == nil && index == len(sonIds)-1 {
				return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
			}
			if editNode.MoveTo != nil && *editNode.MoveTo == index {
				return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
			}

			if editNode.MoveTo == nil || *editNode.MoveTo >= len(sonIds) {
				// Move to end
				var parts []string
				for i, s := range sonIds {
					if i != index {
						parts = append(parts, s)
					}
				}
				parent.SonIds = strings.Join(parts, ",") + "," + selectIdStr
			} else {
				var parts []string
				for i, s := range sonIds {
					if *editNode.MoveTo == i {
						parts = append(parts, selectIdStr)
					}
					if i != index {
						parts = append(parts, s)
					}
				}
				parent.SonIds = strings.Join(parts, ",")
			}
			if err := cs.updateConf(parent, iceId); err != nil {
				return nil, err
			}
		} else {
			// Move to different parent
			moveToParent := cs.serverService.GetMixConfById(app, *moveToParentId, iceId)
			if moveToParent == nil {
				return nil, model.IDNotExist("moveToParentId", *moveToParentId)
			}
			if model.IsLeaf(moveToParent.Type) {
				return nil, model.InputError("move to 该节点不是父节点")
			}
			if cs.serverService.HaveCircle(app, iceId, moveToParent.GetMixId(), *editNode.SelectId) {
				return nil, model.InputError("无法移动，存在循环引用")
			}

			// Add to new parent
			if editNode.MoveTo == nil {
				if moveToParent.SonIds == "" {
					moveToParent.SonIds = selectIdStr
				} else {
					moveToParent.SonIds = moveToParent.SonIds + "," + selectIdStr
				}
			} else {
				if moveToParent.SonIds == "" {
					moveToParent.SonIds = selectIdStr
				} else {
					moveToSonIds := strings.Split(moveToParent.SonIds, ",")
					if *editNode.MoveTo >= len(moveToSonIds) || *editNode.MoveTo < 0 {
						moveToParent.SonIds = moveToParent.SonIds + "," + selectIdStr
					} else {
						var parts []string
						for i, s := range moveToSonIds {
							if *editNode.MoveTo == i {
								parts = append(parts, selectIdStr)
							}
							parts = append(parts, s)
						}
						moveToParent.SonIds = strings.Join(parts, ",")
					}
				}
			}

			// Remove from old parent
			var parts []string
			for i, s := range sonIds {
				if i != index {
					parts = append(parts, s)
				}
			}
			parent.SonIds = strings.Join(parts, ",")
			if err := cs.updateConf(moveToParent, iceId); err != nil {
				return nil, err
			}
			if err := cs.updateConf(parent, iceId); err != nil {
				return nil, err
			}
		}
		return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
	}

	// Move from forward position
	if editNode.NextId != nil {
		next := cs.serverService.GetMixConfById(app, *editNode.NextId, iceId)
		if next == nil {
			return nil, model.IDNotExist("nextId", *editNode.NextId)
		}
		if next.ForwardId == nil || *next.ForwardId != *editNode.SelectId {
			return nil, model.CustomError(fmt.Sprintf("next:%d not have this forward:%d", *editNode.NextId, *editNode.SelectId))
		}

		if editNode.MoveToNextId != nil {
			if *editNode.NextId == *editNode.MoveToNextId {
				return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
			}
			moveToNext := cs.serverService.GetMixConfById(app, *editNode.MoveToNextId, iceId)
			if moveToNext == nil {
				return nil, model.IDNotExist("moveToNextId", *editNode.MoveToNextId)
			}
			if moveToNext.ForwardId != nil {
				return nil, model.CustomError(fmt.Sprintf("move to next:%d already has forward", *editNode.MoveToNextId))
			}
			if cs.serverService.HaveCircle(app, iceId, moveToNext.GetMixId(), *editNode.SelectId) {
				return nil, model.InputError("无法移动，存在循环引用")
			}
			moveToNext.ForwardId = editNode.SelectId
			next.ForwardId = nil
			if err := cs.updateConf(moveToNext, iceId); err != nil {
				return nil, err
			}
			if err := cs.updateConf(next, iceId); err != nil {
				return nil, err
			}
			return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
		}

		if editNode.MoveToParentId != nil {
			sameNode := *editNode.MoveToParentId == *editNode.NextId
			var moveToParent *model.IceConf
			if sameNode {
				moveToParent = next
			} else {
				moveToParent = cs.serverService.GetMixConfById(app, *editNode.MoveToParentId, iceId)
				if moveToParent == nil {
					return nil, model.IDNotExist("moveToParentId", *editNode.MoveToParentId)
				}
			}
			if model.IsLeaf(moveToParent.Type) {
				return nil, model.InputError("move to 该节点不是父节点")
			}
			if cs.serverService.HaveCircle(app, iceId, moveToParent.GetMixId(), *editNode.SelectId) {
				return nil, model.InputError("无法移动，存在循环引用")
			}

			selectIdStr := strconv.FormatInt(*editNode.SelectId, 10)
			if editNode.MoveTo == nil {
				if moveToParent.SonIds == "" {
					moveToParent.SonIds = selectIdStr
				} else {
					moveToParent.SonIds = moveToParent.SonIds + "," + selectIdStr
				}
			} else {
				if moveToParent.SonIds == "" {
					moveToParent.SonIds = selectIdStr
				} else {
					moveToSonIds := strings.Split(moveToParent.SonIds, ",")
					if *editNode.MoveTo >= len(moveToSonIds) || *editNode.MoveTo < 0 {
						moveToParent.SonIds = moveToParent.SonIds + "," + selectIdStr
					} else {
						var parts []string
						for i, s := range moveToSonIds {
							if *editNode.MoveTo == i {
								parts = append(parts, selectIdStr)
							}
							parts = append(parts, s)
						}
						moveToParent.SonIds = strings.Join(parts, ",")
					}
				}
			}
			next.ForwardId = nil
			if sameNode {
				if err := cs.updateConf(next, iceId); err != nil {
					return nil, err
				}
			} else {
				if err := cs.updateConf(moveToParent, iceId); err != nil {
					return nil, err
				}
				if err := cs.updateConf(next, iceId); err != nil {
					return nil, err
				}
			}
			return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
		}
	}
	return &model.EditConfResponse{NodeId: *editNode.SelectId}, nil
}

func (cs *ConfService) assembleMultiplexNodes(app int, iceId int64, lane string, ids []int64) []*model.IceShowNode {
	var nodes []*model.IceShowNode
	for _, id := range ids {
		node := cs.serverService.GetConfMixById(app, id, iceId, lane)
		if node != nil {
			AddUniqueKey(node, "", false, false)
			nodes = append(nodes, node)
		}
	}
	return nodes
}

func (cs *ConfService) createNewConf(editNode *model.IceEditNode, app int) (*model.IceConf, error) {
	confId, err := cs.storage.NextConfId(app)
	if err != nil {
		return nil, err
	}

	inverse := editNode.IsInverse()
	now := model.TimeNowMs()

	nodeType := model.NodeTypeNone
	if editNode.NodeType != nil {
		nodeType = *editNode.NodeType
	}

	conf := &model.IceConf{
		ID:       confId,
		App:      app,
		Type:     nodeType,
		Inverse:  &inverse,
		TimeType: editNode.TimeType,
		Start:    editNode.Start,
		End:      editNode.End,
		Status:   model.Int8Ptr(model.StatusOnline),
		CreateAt: &now,
		UpdateAt: &now,
	}
	if editNode.Name != "" {
		conf.Name = editNode.Name
	}

	if model.IsLeaf(nodeType) {
		leafNodeInfo := cs.leafClassCheck(app, editNode.ConfName, nodeType)
		if editNode.ConfField != "" {
			if checkRes := checkIllegalAndAdjustJson(editNode, leafNodeInfo); checkRes != "" {
				return nil, model.ConfigFieldIllegal(checkRes)
			}
		}
		conf.ConfName = editNode.ConfName
		conf.ConfField = editNode.ConfField
	}

	return conf, nil
}

func (cs *ConfService) saveConfUpdate(conf *model.IceConf, iceId int64) error {
	conf.IceId = &iceId
	conf.ConfId = &conf.ID
	EnsureConfDefaults(conf)
	if conf.CreateAt == nil {
		now := model.TimeNowMs()
		conf.CreateAt = &now
	}
	return cs.storage.SaveConfUpdate(conf.App, iceId, conf)
}

func (cs *ConfService) updateConf(conf *model.IceConf, iceId int64) error {
	now := model.TimeNowMs()
	conf.UpdateAt = &now
	EnsureConfDefaults(conf)
	if !conf.IsUpdating() {
		conf.IceId = &iceId
		conf.ConfId = &conf.ID
	}
	return cs.serverService.UpdateLocalConfUpdateCache(conf)
}

func (cs *ConfService) paramHandle(editNode *model.IceEditNode) error {
	if editNode.App == nil || editNode.IceId == nil || editNode.SelectId == nil || editNode.EditType == nil || !model.IsValidEditType(*editNode.EditType) {
		return model.InputError("app|iceId|selectId|editType")
	}

	tt := model.TimeTypeNone
	if editNode.TimeType != nil {
		tt = *editNode.TimeType
	}
	if !model.IsValidTimeType(tt) {
		editNode.TimeType = model.Int8Ptr(model.TimeTypeNone)
		tt = model.TimeTypeNone
	}

	switch tt {
	case model.TimeTypeNone:
		editNode.Start = nil
		editNode.End = nil
	case model.TimeTypeAfterStart:
		if editNode.Start == nil {
			return model.InputError("start null")
		}
		editNode.End = nil
	case model.TimeTypeBeforeEnd:
		if editNode.End == nil {
			return model.InputError("end null")
		}
		editNode.Start = nil
	case model.TimeTypeBetween:
		if editNode.Start == nil || editNode.End == nil {
			return model.InputError("start|end null")
		}
	}

	if editNode.NodeType != nil && model.IsRelation(*editNode.NodeType) {
		editNode.ConfName = ""
		editNode.ConfField = ""
	}

	if editNode.Name != "" && len(editNode.Name) > 50 {
		return model.InputError("name too long (>50)")
	}

	// Defaults
	if editNode.Inverse == nil {
		editNode.Inverse = model.BoolPtr(false)
	}
	if editNode.TimeType == nil {
		editNode.TimeType = model.Int8Ptr(model.TimeTypeNone)
	}
	return nil
}

func (cs *ConfService) GetConfLeafClass(app int, nodeType int8, lane string) []*model.IceLeafClass {
	clazzMap := cs.clientManager.GetLeafTypeClasses(app, nodeType, lane)
	if len(clazzMap) == 0 {
		return []*model.IceLeafClass{}
	}
	var result []*model.IceLeafClass
	for clazz, info := range clazzMap {
		order := 100
		if info.Order != nil {
			order = *info.Order
		}
		result = append(result, &model.IceLeafClass{
			FullName: clazz,
			Name:     info.Name,
			Order:    order,
		})
	}
	sort.Slice(result, func(i, j int) bool {
		return result[i].Order < result[j].Order
	})
	return result
}

func (cs *ConfService) leafClassCheck(app int, clazz string, nodeType int8) *model.LeafNodeInfo {
	if clazz == "" {
		return nil
	}
	clazzMap := cs.clientManager.GetLeafTypeClasses(app, nodeType, "")
	if len(clazzMap) == 0 {
		return nil
	}
	return clazzMap[clazz]
}

func (cs *ConfService) LeafClassCheckAPI(app int, clazz string, nodeType int8) error {
	if clazz == "" || !model.IsLeaf(nodeType) {
		return model.InputError("app|clazz|type")
	}
	cs.leafClassCheck(app, clazz, nodeType)
	return nil
}

func (cs *ConfService) ConfDetail(app int, confId int64, address string, iceId int64, lane string) (*model.IceShowConf, error) {
	root := cs.serverService.GetConfMixById(app, confId, iceId, lane)
	if root == nil {
		return nil, model.ConfNotFound(app, "confId", confId)
	}
	showConf := &model.IceShowConf{
		App:  app,
		Root: root,
	}
	AddUniqueKey(root, "", true, false)
	count := CountUpdating(root)
	showConf.UpdateCount = &count
	return showConf, nil
}

func CountUpdating(node *model.IceShowNode) int {
	if node == nil {
		return 0
	}
	count := 0
	if node.ShowConf != nil && node.ShowConf.Updating != nil && *node.ShowConf.Updating {
		count++
	}
	if node.Forward != nil {
		count += CountUpdating(node.Forward)
	}
	for _, child := range node.Children {
		count += CountUpdating(child)
	}
	return count
}

func AddUniqueKey(node *model.IceShowNode, prefix string, root, forward bool) {
	if node == nil || node.ShowConf == nil {
		return
	}
	idx := 0
	if node.Index != nil {
		idx = *node.Index
	}
	uniqueKey := fmt.Sprintf("%d_%d", node.ShowConf.NodeId, idx)
	if prefix != "" {
		uniqueKey = prefix + "_" + uniqueKey
	}
	if root {
		uniqueKey += "_r"
	}
	if forward {
		uniqueKey += "_f"
	}
	node.ShowConf.UniqueKey = uniqueKey

	if node.Forward != nil {
		AddUniqueKey(node.Forward, uniqueKey, false, true)
	}
	for _, child := range node.Children {
		AddUniqueKey(child, uniqueKey, false, false)
	}
}

// checkIllegalAndAdjustJson validates and adjusts JSON config
func checkIllegalAndAdjustJson(editNode *model.IceEditNode, nodeInfo *model.LeafNodeInfo) string {
	var obj map[string]any
	decoder := json.NewDecoder(bytes.NewReader([]byte(editNode.ConfField)))
	decoder.UseNumber()
	if err := decoder.Decode(&obj); err != nil {
		return "json illegal"
	}

	if nodeInfo != nil {
		// Re-serialize to normalize
		result := make(map[string]any)
		for k, v := range obj {
			fieldType := getFieldType(k, nodeInfo)
			if fieldType == "" {
				result[k] = v
				continue
			}
			// If value is string but field type is not string, try to parse
			if strVal, ok := v.(string); ok && !isStringType(fieldType) {
				if isObjectType(fieldType) {
					if len(strVal) > 1 && strings.HasPrefix(strVal, "\"") && strings.HasSuffix(strVal, "\"") {
						result[k] = strVal[1 : len(strVal)-1]
						continue
					}
				}
				var parsed any
				innerDec := json.NewDecoder(bytes.NewReader([]byte(strVal)))
				innerDec.UseNumber()
				if err := innerDec.Decode(&parsed); err != nil {
					if isObjectType(fieldType) {
						result[k] = v
						continue
					}
					return fmt.Sprintf("filed:%s type:%s input:%s", k, fieldType, strVal)
				}
				result[k] = parsed
			} else {
				result[k] = v
			}
		}
		adjusted, err := json.Marshal(result)
		if err == nil {
			editNode.ConfField = string(adjusted)
		}
	}
	return ""
}

var stringTypes = map[string]bool{
	"java.lang.String": true, "string": true, "str": true,
}

var objectTypes = map[string]bool{
	"java.lang.Object": true, "object": true, "any": true,
}

func isStringType(t string) bool { return stringTypes[t] }
func isObjectType(t string) bool { return objectTypes[t] }

func getFieldType(fieldName string, nodeInfo *model.LeafNodeInfo) string {
	for _, f := range nodeInfo.IceFields {
		if f.Field == fieldName {
			return f.Type
		}
	}
	for _, f := range nodeInfo.HideFields {
		if f.Field == fieldName {
			return f.Type
		}
	}
	return ""
}

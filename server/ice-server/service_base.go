package main

import (
	"encoding/json"
	"sort"
	"strings"
)

type BaseService struct {
	storage       *Storage
	serverService *ServerService
}

func NewBaseService(storage *Storage, serverService *ServerService) *BaseService {
	return &BaseService{storage: storage, serverService: serverService}
}

func (bs *BaseService) BaseList(search *IceBaseSearch) (*PageResult, error) {
	allBases, err := bs.storage.ListBases(search.App)
	if err != nil {
		return nil, err
	}

	// Filter
	var filtered []*IceBase
	for _, b := range allBases {
		if b.Status == nil || *b.Status != StatusOnline {
			continue
		}
		if search.BaseId != nil {
			if b.ID != nil && *b.ID == *search.BaseId {
				filtered = append(filtered, b)
			}
			continue
		}
		match := true
		if search.Name != "" {
			match = b.Name != "" && strings.HasPrefix(b.Name, search.Name)
		}
		if match && search.Scene != "" {
			if b.Scenes == "" {
				match = false
			} else {
				scenes := strings.Split(b.Scenes, ",")
				found := false
				for _, s := range scenes {
					if s == search.Scene {
						found = true
						break
					}
				}
				match = found
			}
		}
		if match {
			filtered = append(filtered, b)
		}
	}

	// Sort by ID descending
	sort.Slice(filtered, func(i, j int) bool {
		return *filtered[i].ID > *filtered[j].ID
	})

	// Paginate
	total := len(filtered)
	start := (search.PageNum - 1) * search.PageSize
	end := start + search.PageSize
	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	var list interface{}
	if start < total {
		list = filtered[start:end]
	} else {
		list = []*IceBase{}
	}

	return NewPageResult(list, int64(total), search.PageNum, search.PageSize), nil
}

func (bs *BaseService) BaseCreate(base *IceBase) (int64, error) {
	return bs.BaseCreateAtPath(base, "")
}

// BaseCreateAtPath creates a base at a specific folder path (relative to bases/)
func (bs *BaseService) BaseCreateAtPath(base *IceBase, path string) (int64, error) {
	if base.App == nil {
		return 0, InputError("app required")
	}
	transferDto := &IceTransferDto{}

	if base.ID != nil {
		existing, _ := bs.storage.GetBase(*base.App, *base.ID)
		if existing != nil && existing.Status != nil && *existing.Status != StatusDeleted {
			return 0, AlreadyExist("iceId")
		}
		if err := bs.storage.EnsureBaseIdNotLessThan(*base.App, *base.ID); err != nil {
			return 0, err
		}
	} else {
		nextId, err := bs.storage.NextBaseId(*base.App)
		if err != nil {
			return 0, err
		}
		base.ID = &nextId
	}

	now := timeNowMs()
	base.CreateAt = &now
	if base.Debug == nil {
		base.Debug = Int8Ptr(0)
	}
	if base.Scenes == "" {
		base.Scenes = ""
	}
	if base.Status == nil {
		base.Status = Int8Ptr(StatusOnline)
	}
	if base.TimeType == nil {
		base.TimeType = Int8Ptr(TimeTypeNone)
	}
	if base.Priority == nil {
		base.Priority = Int64Ptr(1)
	}

	if base.ConfID == nil {
		confId, err := bs.storage.NextConfId(*base.App)
		if err != nil {
			return 0, err
		}
		createConf := &IceConf{
			ID:       confId,
			App:      *base.App,
			Status:   Int8Ptr(StatusOnline),
			Type:     NodeTypeNone,
			Inverse:  BoolPtr(false),
			Debug:    Int8Ptr(1),
			TimeType: Int8Ptr(TimeTypeNone),
			CreateAt: &now,
			UpdateAt: &now,
		}
		if err := bs.storage.SaveConf(createConf); err != nil {
			return 0, err
		}
		transferDto.InsertOrUpdateConfs = []*IceConf{createConf}
		base.ConfID = &confId
	}

	if err := timeCheck(base); err != nil {
		return 0, err
	}
	base.UpdateAt = &now

	// Save at specified path (or root if empty)
	if err := bs.storage.SaveBaseAtPath(base, path); err != nil {
		return 0, err
	}
	transferDto.InsertOrUpdateBases = []*IceBase{base}

	newVersion, err := bs.serverService.GetAndIncrementVersion(*base.App)
	if err != nil {
		return 0, err
	}
	transferDto.Version = newVersion
	bs.storage.SaveVersionUpdate(*base.App, newVersion, transferDto)

	return *base.ID, nil
}

func (bs *BaseService) BaseEdit(base *IceBase) (int64, error) {
	if base.App == nil {
		return 0, InputError("app required")
	}
	if base.ID == nil {
		return 0, InputError("id required for edit")
	}
	origin, err := bs.storage.GetBase(*base.App, *base.ID)
	if err != nil {
		return 0, err
	}
	if origin == nil || (origin.Status != nil && *origin.Status == StatusOffline) {
		return 0, IDNotExist("iceId", *base.ID)
	}

	transferDto := &IceTransferDto{}

	base.ConfID = origin.ConfID
	base.CreateAt = origin.CreateAt
	if base.Debug == nil {
		base.Debug = origin.Debug
	}
	if base.Scenes == "" {
		base.Scenes = origin.Scenes
	}
	if base.Status == nil {
		base.Status = origin.Status
	}
	if base.TimeType == nil {
		base.TimeType = origin.TimeType
	}
	if base.Priority == nil {
		base.Priority = origin.Priority
	}

	if err := timeCheck(base); err != nil {
		return 0, err
	}
	now := timeNowMs()
	base.UpdateAt = &now
	if err := bs.storage.SaveBase(base); err != nil {
		return 0, err
	}

	if base.Status != nil && *base.Status == StatusOnline {
		transferDto.InsertOrUpdateBases = []*IceBase{base}
	} else {
		transferDto.DeleteBaseIds = []int64{*base.ID}
	}

	newVersion, err := bs.serverService.GetAndIncrementVersion(*base.App)
	if err != nil {
		return 0, err
	}
	transferDto.Version = newVersion
	bs.storage.SaveVersionUpdate(*base.App, newVersion, transferDto)

	return *base.ID, nil
}

func timeCheck(base *IceBase) error {
	now := timeNowMs()
	base.UpdateAt = &now

	tt := TimeTypeNone
	if base.TimeType != nil {
		tt = *base.TimeType
	}
	if !IsValidTimeType(tt) {
		base.TimeType = Int8Ptr(TimeTypeNone)
		tt = TimeTypeNone
	}

	switch tt {
	case TimeTypeNone:
		base.Start = nil
		base.End = nil
	case TimeTypeAfterStart:
		if base.Start == nil {
			return InputError("start null")
		}
		base.End = nil
	case TimeTypeBeforeEnd:
		if base.End == nil {
			return InputError("end null")
		}
		base.Start = nil
	case TimeTypeBetween:
		if base.Start == nil || base.End == nil {
			return InputError("start|end null")
		}
	}
	return nil
}

func (bs *BaseService) Push(app int, iceId int64, reason string) (int64, error) {
	base, err := bs.storage.GetBase(app, iceId)
	if err != nil {
		return 0, err
	}
	if base == nil || (base.App != nil && *base.App != app) {
		return 0, IDNotExist("iceId", iceId)
	}

	pushData := bs.getPushData(base)
	pushDataJson, err := json.Marshal(pushData)
	if err != nil {
		return 0, err
	}

	pushId, err := bs.storage.NextPushId(app)
	if err != nil {
		return 0, err
	}
	now := timeNowMs()
	history := &IcePushHistory{
		ID:       &pushId,
		App:      app,
		IceId:    iceId,
		Reason:   reason,
		Operator: "waitmoon",
		CreateAt: &now,
		PushData: string(pushDataJson),
	}
	if err := bs.storage.SavePushHistory(history); err != nil {
		return 0, err
	}
	return pushId, nil
}

func (bs *BaseService) getPushData(base *IceBase) *PushData {
	pushData := &PushData{
		App:  *base.App,
		Base: baseToDtoWithName(base),
	}

	confUpdates := bs.serverService.GetAllUpdateConfList(*base.App, *base.ID)
	if len(confUpdates) > 0 {
		dtos := make([]*IceConf, len(confUpdates))
		for i, c := range confUpdates {
			dtos[i] = confToDtoWithName(c)
		}
		pushData.ConfUpdates = dtos
	}

	if base.ConfID != nil {
		activeConfs := bs.serverService.GetAllActiveConfSet(*base.App, *base.ConfID)
		if len(activeConfs) > 0 {
			dtos := make([]*IceConf, len(activeConfs))
			for i, c := range activeConfs {
				dtos[i] = confToDtoWithName(c)
			}
			pushData.Confs = dtos
		}
	}
	return pushData
}

func (bs *BaseService) History(app int, iceId *int64, pageNum, pageSize int) (*PageResult, error) {
	all, err := bs.storage.ListPushHistories(app, iceId)
	if err != nil {
		return nil, err
	}

	total := len(all)
	start := (pageNum - 1) * pageSize
	end := start + pageSize
	if start > total {
		start = total
	}
	if end > total {
		end = total
	}

	var list interface{}
	if start < total {
		list = all[start:end]
	} else {
		list = []*IcePushHistory{}
	}
	return NewPageResult(list, int64(total), pageNum, pageSize), nil
}

func (bs *BaseService) ExportData(app int, iceId int64, pushId *int64) (string, error) {
	if pushId != nil && *pushId > 0 {
		history, err := bs.storage.GetPushHistory(app, *pushId)
		if err != nil {
			return "", err
		}
		if history != nil {
			return history.PushData, nil
		}
		return "", IDNotExist("pushId", *pushId)
	}

	base, err := bs.storage.GetBase(app, iceId)
	if err != nil {
		return "", err
	}
	if base != nil {
		pushData := bs.getPushData(base)
		jsonData, err := json.Marshal(pushData)
		if err != nil {
			return "", err
		}
		return string(jsonData), nil
	}
	return "", IDNotExist("iceId", iceId)
}

func (bs *BaseService) ExportBatchData(app int, iceIds []int64) (string, error) {
	var pushDataList []*PushData
	for _, iceId := range iceIds {
		base, err := bs.storage.GetBase(app, iceId)
		if err != nil {
			continue
		}
		if base != nil {
			pushDataList = append(pushDataList, bs.getPushData(base))
		}
	}
	jsonData, err := json.Marshal(pushDataList)
	if err != nil {
		return "", err
	}
	return string(jsonData), nil
}

func (bs *BaseService) Rollback(app int, pushId int64) error {
	history, err := bs.storage.GetPushHistory(app, pushId)
	if err != nil {
		return err
	}
	if history == nil {
		return IDNotExist("pushId", pushId)
	}

	var pushData PushData
	if err := json.Unmarshal([]byte(history.PushData), &pushData); err != nil {
		return err
	}
	return bs.ImportData(&pushData)
}

func (bs *BaseService) ImportData(data *PushData) error {
	now := timeNowMs()

	// Import confUpdates
	for _, confUpdate := range data.ConfUpdates {
		confUpdate.App = data.App
		confUpdate.UpdateAt = &now
		if confUpdate.Status == nil {
			confUpdate.Status = Int8Ptr(StatusOnline)
		}
		if confUpdate.IceId != nil {
			if err := bs.storage.SaveConfUpdate(data.App, *confUpdate.IceId, confUpdate); err != nil {
				return err
			}
		}
	}

	// Import confs
	for _, conf := range data.Confs {
		oldConf, _ := bs.storage.GetConf(data.App, conf.ID)
		conf.App = data.App
		conf.UpdateAt = &now
		if conf.Status == nil {
			conf.Status = Int8Ptr(StatusOnline)
		}
		if oldConf != nil && oldConf.CreateAt != nil {
			conf.CreateAt = oldConf.CreateAt
		} else if conf.CreateAt == nil {
			conf.CreateAt = &now
		}
		if err := bs.storage.SaveConf(conf); err != nil {
			return err
		}
	}

	// Import base
	if data.Base != nil {
		base := data.Base
		oldBase, _ := bs.storage.GetBase(data.App, *base.ID)
		base.App = &data.App
		base.UpdateAt = &now
		if base.Status == nil {
			base.Status = Int8Ptr(StatusOnline)
		}
		if oldBase != nil && oldBase.CreateAt != nil {
			base.CreateAt = oldBase.CreateAt
		} else if base.CreateAt == nil {
			base.CreateAt = &now
		}
		if base.TimeType == nil {
			base.TimeType = Int8Ptr(TimeTypeNone)
		}
		if base.Priority == nil {
			base.Priority = Int64Ptr(1)
		}
		if err := bs.storage.SaveBase(base); err != nil {
			return err
		}

		// Version update
		transferDto := &IceTransferDto{}
		if len(data.Confs) > 0 {
			transferDto.InsertOrUpdateConfs = data.Confs
		}
		if base.Status != nil && *base.Status == StatusOnline {
			transferDto.InsertOrUpdateBases = []*IceBase{base}
		} else {
			transferDto.DeleteBaseIds = []int64{*base.ID}
		}
		newVersion, err := bs.serverService.GetAndIncrementVersion(data.App)
		if err != nil {
			return err
		}
		transferDto.Version = newVersion
		bs.storage.SaveVersionUpdate(data.App, newVersion, transferDto)
	}

	return nil
}

func (bs *BaseService) Delete(app int, pushId int64) error {
	return bs.storage.DeletePushHistory(app, pushId)
}


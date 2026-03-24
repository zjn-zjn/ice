package service

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/storage"
)

type BaseService struct {
	storage       *storage.Storage
	serverService *ServerService
}

func NewBaseService(storage *storage.Storage, serverService *ServerService) *BaseService {
	return &BaseService{storage: storage, serverService: serverService}
}

func (bs *BaseService) BaseList(search *model.IceBaseSearch) (*model.PageResult, error) {
	allBases, err := bs.storage.ListBases(search.App)
	if err != nil {
		return nil, err
	}

	// Filter
	var filtered []*model.IceBase
	for _, b := range allBases {
		if b.Status == nil || *b.Status != model.StatusOnline {
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
		if filtered[i].ID == nil || filtered[j].ID == nil {
			return filtered[i].ID != nil
		}
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

	var list any
	if start < total {
		list = filtered[start:end]
	} else {
		list = []*model.IceBase{}
	}

	return model.NewPageResult(list, int64(total), search.PageNum, search.PageSize), nil
}

func (bs *BaseService) BaseCreate(base *model.IceBase) (int64, error) {
	return bs.BaseCreateAtPath(base, "")
}

// BaseCreateAtPath creates a base at a specific folder path (relative to bases/)
func (bs *BaseService) BaseCreateAtPath(base *model.IceBase, path string) (int64, error) {
	if base.App == nil {
		return 0, model.InputError("app required")
	}
	transferDto := &model.IceTransferDto{}

	if base.ID != nil {
		existing, _ := bs.storage.GetBase(*base.App, *base.ID)
		if existing != nil && existing.Status != nil && *existing.Status != model.StatusDeleted {
			return 0, model.AlreadyExist("iceId")
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

	now := model.TimeNowMs()
	base.CreateAt = &now
	if base.Debug == nil {
		base.Debug = model.Int8Ptr(0)
	}
	// Scenes can be empty, no default needed
	if base.Status == nil {
		base.Status = model.Int8Ptr(model.StatusOnline)
	}
	if base.TimeType == nil {
		base.TimeType = model.Int8Ptr(model.TimeTypeNone)
	}
	if base.ConfID == nil {
		confId, err := bs.storage.NextConfId(*base.App)
		if err != nil {
			return 0, err
		}
		createConf := &model.IceConf{
			ID:       confId,
			App:      *base.App,
			Status:   model.Int8Ptr(model.StatusOnline),
			Type:     model.NodeTypeNone,
			Inverse:  model.BoolPtr(false),
			TimeType: model.Int8Ptr(model.TimeTypeNone),
			CreateAt: &now,
			UpdateAt: &now,
		}
		if err := bs.storage.SaveConf(createConf); err != nil {
			return 0, err
		}
		transferDto.InsertOrUpdateConfs = []*model.IceConf{createConf}
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
	transferDto.InsertOrUpdateBases = []*model.IceBase{base}

	newVersion, err := bs.serverService.GetAndIncrementVersion(*base.App)
	if err != nil {
		return 0, err
	}
	transferDto.Version = newVersion
	if err := bs.storage.SaveVersionUpdate(*base.App, newVersion, transferDto); err != nil {
		return 0, err
	}

	return *base.ID, nil
}

func (bs *BaseService) BaseEdit(base *model.IceBase) (int64, error) {
	if base.App == nil {
		return 0, model.InputError("app required")
	}
	if base.ID == nil {
		return 0, model.InputError("id required for edit")
	}
	origin, err := bs.storage.GetBase(*base.App, *base.ID)
	if err != nil {
		return 0, err
	}
	if origin == nil || (origin.Status != nil && *origin.Status != model.StatusOnline) {
		return 0, model.IDNotExist("iceId", *base.ID)
	}

	transferDto := &model.IceTransferDto{}

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
	if err := timeCheck(base); err != nil {
		return 0, err
	}
	now := model.TimeNowMs()
	base.UpdateAt = &now
	if err := bs.storage.SaveBase(base); err != nil {
		return 0, err
	}

	if base.Status != nil && *base.Status == model.StatusOnline {
		transferDto.InsertOrUpdateBases = []*model.IceBase{base}
	} else {
		transferDto.DeleteBaseIds = []int64{*base.ID}
	}

	newVersion, err := bs.serverService.GetAndIncrementVersion(*base.App)
	if err != nil {
		return 0, err
	}
	transferDto.Version = newVersion
	if err := bs.storage.SaveVersionUpdate(*base.App, newVersion, transferDto); err != nil {
		return 0, err
	}

	return *base.ID, nil
}

func timeCheck(base *model.IceBase) error {
	now := model.TimeNowMs()
	base.UpdateAt = &now

	tt := model.TimeTypeNone
	if base.TimeType != nil {
		tt = *base.TimeType
	}
	if !model.IsValidTimeType(tt) {
		base.TimeType = model.Int8Ptr(model.TimeTypeNone)
		tt = model.TimeTypeNone
	}

	switch tt {
	case model.TimeTypeNone:
		base.Start = nil
		base.End = nil
	case model.TimeTypeAfterStart:
		if base.Start == nil {
			return model.InputError("start null")
		}
		base.End = nil
	case model.TimeTypeBeforeEnd:
		if base.End == nil {
			return model.InputError("end null")
		}
		base.Start = nil
	case model.TimeTypeBetween:
		if base.Start == nil || base.End == nil {
			return model.InputError("start|end null")
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
		return 0, model.IDNotExist("iceId", iceId)
	}

	pushData := bs.buildPushData(app, []*model.IceBase{base})
	pushDataJson, err := json.Marshal(pushData)
	if err != nil {
		return 0, err
	}

	pushId, err := bs.storage.NextPushId(app)
	if err != nil {
		return 0, err
	}
	now := model.TimeNowMs()
	history := &model.IcePushHistory{
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

func (bs *BaseService) buildPushData(app int, bases []*model.IceBase) *model.PushData {
	pushData := &model.PushData{App: app}

	// Collect unique confIds
	confIdSet := make(map[int64]bool)
	for _, base := range bases {
		baseDto := BaseToDtoWithName(base)
		baseDto.Path = bs.storage.GetBaseRelDir(app, *base.ID)
		pushData.Bases = append(pushData.Bases, baseDto)
		if base.ConfID != nil {
			confIdSet[*base.ConfID] = true
		}
	}

	// Query each unique conf tree once
	visited := make(map[int64]bool)
	for confId := range confIdSet {
		activeConfs := bs.serverService.GetAllActiveConfSet(app, confId)
		for _, c := range activeConfs {
			if !visited[c.ID] {
				visited[c.ID] = true
				pushData.Confs = append(pushData.Confs, ConfToDtoWithName(c))
			}
		}
	}
	return pushData
}

func (bs *BaseService) History(app int, iceId *int64, pageNum, pageSize int) (*model.PageResult, error) {
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

	var list any
	if start < total {
		list = all[start:end]
	} else {
		list = []*model.IcePushHistory{}
	}
	return model.NewPageResult(list, int64(total), pageNum, pageSize), nil
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
		return "", model.IDNotExist("pushId", *pushId)
	}
	return bs.ExportBatchData(app, []int64{iceId})
}

func (bs *BaseService) ExportBatchData(app int, iceIds []int64) (string, error) {
	var bases []*model.IceBase
	for _, iceId := range iceIds {
		base, err := bs.storage.GetBase(app, iceId)
		if err != nil {
			return "", err
		}
		if base != nil {
			bases = append(bases, base)
		}
	}
	pushData := bs.buildPushData(app, bases)
	jsonData, err := json.Marshal(pushData)
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
		return model.IDNotExist("pushId", pushId)
	}

	var pushData model.PushData
	if err := json.Unmarshal([]byte(history.PushData), &pushData); err != nil {
		return err
	}
	return bs.ImportData(&pushData)
}

func (bs *BaseService) ImportData(data *model.PushData) error {
	now := model.TimeNowMs()

	// Compatible with old format: base -> bases
	if data.Base != nil {
		if data.Base.Path == "" && data.Path != "" {
			data.Base.Path = data.Path
		}
		data.Bases = append(data.Bases, data.Base)
		data.Base = nil
	}

	// Import confs
	var maxConfId int64
	for _, conf := range data.Confs {
		oldConf, _ := bs.storage.GetConf(data.App, conf.ID)
		conf.App = data.App
		conf.UpdateAt = &now
		if conf.Status == nil {
			conf.Status = model.Int8Ptr(model.StatusOnline)
		}
		if oldConf != nil && oldConf.CreateAt != nil {
			conf.CreateAt = oldConf.CreateAt
		} else if conf.CreateAt == nil {
			conf.CreateAt = &now
		}
		if err := bs.storage.SaveConf(conf); err != nil {
			return err
		}
		if conf.ID > maxConfId {
			maxConfId = conf.ID
		}
	}
	if maxConfId > 0 {
		if err := bs.storage.EnsureConfIdNotLessThan(data.App, maxConfId); err != nil {
			return err
		}
	}

	// Import bases
	var maxBaseId int64
	transferDto := &model.IceTransferDto{}
	if len(data.Confs) > 0 {
		transferDto.InsertOrUpdateConfs = data.Confs
	}
	for _, base := range data.Bases {
		path := base.Path
		base.Path = "" // don't persist path in base json
		oldBase, _ := bs.storage.GetBase(data.App, *base.ID)
		base.App = &data.App
		base.UpdateAt = &now
		if base.Status == nil {
			base.Status = model.Int8Ptr(model.StatusOnline)
		}
		if oldBase != nil && oldBase.CreateAt != nil {
			base.CreateAt = oldBase.CreateAt
		} else if base.CreateAt == nil {
			base.CreateAt = &now
		}
		if base.TimeType == nil {
			base.TimeType = model.Int8Ptr(model.TimeTypeNone)
		}
		if oldBase == nil && path != "" {
			basesDir := bs.storage.BasesDir(data.App)
			targetDir := filepath.Join(basesDir, path)
			os.MkdirAll(targetDir, 0755)
			if err := bs.storage.SaveBaseAtPath(base, path); err != nil {
				return err
			}
		} else {
			if err := bs.storage.SaveBase(base); err != nil {
				return err
			}
		}
		if *base.ID > maxBaseId {
			maxBaseId = *base.ID
		}
		if base.Status != nil && *base.Status == model.StatusOnline {
			transferDto.InsertOrUpdateBases = append(transferDto.InsertOrUpdateBases, base)
		} else {
			transferDto.DeleteBaseIds = append(transferDto.DeleteBaseIds, *base.ID)
		}
	}
	if maxBaseId > 0 {
		if err := bs.storage.EnsureBaseIdNotLessThan(data.App, maxBaseId); err != nil {
			return err
		}
	}

	// Version update once
	if len(data.Bases) > 0 {
		newVersion, err := bs.serverService.GetAndIncrementVersion(data.App)
		if err != nil {
			return err
		}
		transferDto.Version = newVersion
		if err := bs.storage.SaveVersionUpdate(data.App, newVersion, transferDto); err != nil {
			return err
		}
	}

	return nil
}

func (bs *BaseService) Delete(app int, pushId int64) error {
	return bs.storage.DeletePushHistory(app, pushId)
}

// DeleteBase deletes a base by ID (hard delete)
func (bs *BaseService) DeleteBase(app int, baseId int64) error {
	return bs.storage.DeleteBase(app, baseId, true)
}

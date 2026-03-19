package service

import (
	"sort"
	"strings"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/storage"
)

type AppService struct {
	storage       *storage.Storage
	clientManager *ClientManager
}

func NewAppService(storage *storage.Storage, clientManager *ClientManager) *AppService {
	return &AppService{storage: storage, clientManager: clientManager}
}

func (as *AppService) AppEdit(app *model.IceApp) (int, error) {
	if app.ID == nil {
		// Create new app
		nextId, err := as.storage.NextAppId()
		if err != nil {
			return 0, err
		}
		app.ID = &nextId
		if app.Status == nil {
			app.Status = model.Int8Ptr(model.StatusOnline)
		}
		now := model.TimeNowMs()
		app.CreateAt = &now
	} else {
		// Edit existing app
		existing, err := as.storage.GetApp(*app.ID)
		if err != nil {
			return 0, err
		}
		if existing == nil {
			return 0, model.IDNotExist("app", *app.ID)
		}
		if app.Status == nil {
			app.Status = existing.Status
		}
		if existing.CreateAt != nil {
			app.CreateAt = existing.CreateAt
		}
	}

	now := model.TimeNowMs()
	app.UpdateAt = &now

	// Ensure directories
	as.storage.EnsureAppDirectories(*app.ID)

	if err := as.storage.SaveApp(app); err != nil {
		return 0, err
	}
	return *app.ID, nil
}

func (as *AppService) AppList(pageNum, pageSize int, name string, appId *int) (*model.PageResult, error) {
	apps, err := as.storage.ListApps()
	if err != nil {
		return nil, err
	}

	// Filter
	var filtered []*model.IceApp
	for _, app := range apps {
		if appId != nil {
			if app.ID != nil && *app.ID == *appId {
				filtered = append(filtered, app)
			}
			continue
		}
		if name != "" {
			if app.Name == "" || !strings.Contains(app.Name, name) {
				continue
			}
		}
		filtered = append(filtered, app)
	}

	// Sort by ID ascending
	sort.Slice(filtered, func(i, j int) bool {
		if filtered[i].ID == nil || filtered[j].ID == nil {
			return false
		}
		return *filtered[i].ID < *filtered[j].ID
	})

	// Paginate
	total := len(filtered)
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
		list = filtered[start:end]
	} else {
		list = []*model.IceApp{}
	}

	return model.NewPageResult(list, int64(total), pageNum, pageSize), nil
}

func (as *AppService) GetRegisterClients(app int) map[string]bool {
	return as.clientManager.GetRegisterClients(app)
}

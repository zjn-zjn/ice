package main

import (
	"sort"
	"strings"
)

type AppService struct {
	storage       *Storage
	clientManager *ClientManager
}

func NewAppService(storage *Storage, clientManager *ClientManager) *AppService {
	return &AppService{storage: storage, clientManager: clientManager}
}

func (as *AppService) AppEdit(app *IceApp) (int, error) {
	if app.ID == nil {
		// Create new app
		nextId, err := as.storage.NextAppId()
		if err != nil {
			return 0, err
		}
		app.ID = &nextId
		if app.Status == nil {
			app.Status = Int8Ptr(StatusOnline)
		}
		now := timeNowMs()
		app.CreateAt = &now
	} else {
		// Edit existing app
		existing, err := as.storage.GetApp(*app.ID)
		if err != nil {
			return 0, err
		}
		if existing == nil {
			return 0, IDNotExist("app", *app.ID)
		}
		if app.Status == nil {
			app.Status = existing.Status
		}
		if existing.CreateAt != nil {
			app.CreateAt = existing.CreateAt
		}
	}

	now := timeNowMs()
	app.UpdateAt = &now

	// Ensure directories
	as.storage.ensureAppDirectories(*app.ID)

	if err := as.storage.SaveApp(app); err != nil {
		return 0, err
	}
	return *app.ID, nil
}

func (as *AppService) AppList(pageNum, pageSize int, name string, appId *int) (*PageResult, error) {
	apps, err := as.storage.ListApps()
	if err != nil {
		return nil, err
	}

	// Filter
	var filtered []*IceApp
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
		list = []*IceApp{}
	}

	return NewPageResult(list, int64(total), pageNum, pageSize), nil
}

func (as *AppService) GetRegisterClients(app int) map[string]bool {
	return as.clientManager.GetRegisterClients(app)
}

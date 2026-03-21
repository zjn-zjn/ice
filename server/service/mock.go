package service

import (
	"fmt"
	"math/rand"
	"path/filepath"
	"strings"
	"time"

	"github.com/waitmoon/ice-server/model"
	"github.com/waitmoon/ice-server/storage"
)

var errClientOffline = fmt.Errorf("client offline")

type MockService struct {
	storage       *storage.Storage
	clientManager *ClientManager
}

func NewMockService(storage *storage.Storage, clientManager *ClientManager) *MockService {
	return &MockService{storage: storage, clientManager: clientManager}
}

func (s *MockService) Execute(req *model.MockExecuteRequest) (*model.MockResult, error) {
	address, err := s.resolveTarget(req.App, req.Target)
	if err != nil {
		if err == errClientOffline {
			return &model.MockResult{
				Success:  false,
				Fallback: true,
				Error:    "目标客户端已下线",
			}, nil
		}
		return nil, err
	}

	mockId := randomId(5)
	now := model.TimeNowMs()

	mockReq := &model.MockRequest{
		MockId:   mockId,
		App:      req.App,
		IceId:    req.IceId,
		ConfId:   req.ConfId,
		Scene:    req.Scene,
		Ts:       req.Ts,
		Debug:    req.Debug,
		Roam:     req.Roam,
		CreateAt: now,
	}

	if err := s.storage.WriteMockRequest(req.App, address, mockReq); err != nil {
		return nil, fmt.Errorf("failed to write mock request: %w", err)
	}

	// Poll for result
	mockDir := s.storage.MockDir(req.App, address)
	resultPath := filepath.Join(mockDir, mockId+"_result"+storage.SuffixJson)
	requestPath := filepath.Join(mockDir, mockId+storage.SuffixJson)

	timeout := 60 * time.Second
	interval := 1 * time.Second
	deadline := time.Now().Add(timeout)

	for time.Now().Before(deadline) {
		time.Sleep(interval)

		result, err := s.storage.ReadMockResult(req.App, address, mockId)
		if err != nil {
			continue
		}
		if result != nil {
			// Clean up files
			s.storage.DeleteMockFile(resultPath)
			s.storage.DeleteMockFile(requestPath)
			return result, nil
		}
	}

	// Timeout - clean up request file
	s.storage.DeleteMockFile(requestPath)
	return &model.MockResult{
		MockId:    mockId,
		Success:   false,
		Error:     "timeout waiting for client response",
		ExecuteAt: model.TimeNowMs(),
	}, nil
}

func (s *MockService) resolveTarget(app int, target string) (string, error) {
	if target == "" || target == "all" {
		client := s.clientManager.getValidLatestClient(app, "")
		if client == nil {
			return "", fmt.Errorf("no active client found for app %d", app)
		}
		return client.Address, nil
	}

	if strings.HasPrefix(target, "lane:") {
		lane := strings.TrimPrefix(target, "lane:")
		client := s.clientManager.getValidLatestClient(app, lane)
		if client == nil {
			return "", fmt.Errorf("no active client found for app %d lane %s", app, lane)
		}
		return client.Address, nil
	}

	if strings.HasPrefix(target, "address:") {
		addr := strings.TrimPrefix(target, "address:")
		client, _ := s.clientManager.storage.GetClient(app, "", addr)
		if client == nil || !s.clientManager.isClientActive(client) {
			return "", errClientOffline
		}
		return addr, nil
	}

	return target, nil
}

const idChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

func randomId(n int) string {
	b := make([]byte, n)
	for i := range b {
		b[i] = idChars[rand.Intn(len(idChars))]
	}
	return string(b)
}

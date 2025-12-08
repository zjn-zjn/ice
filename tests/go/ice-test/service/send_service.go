// Package service provides mock services for testing.
package service

import (
	"log/slog"
)

// SendService provides mock send operations.
type SendService struct{}

// NewSendService creates a new SendService.
func NewSendService() *SendService {
	return &SendService{}
}

// SendAmount simulates sending amount to a user.
func (s *SendService) SendAmount(uid int, value float64) bool {
	slog.Info("=======send amount", "uid", uid, "value", value)
	return true
}

// SendPoint simulates sending points to a user.
func (s *SendService) SendPoint(uid int, value float64) bool {
	slog.Info("=======send point", "uid", uid, "value", value)
	return true
}


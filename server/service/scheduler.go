package service

import (
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/waitmoon/ice-server/config"
	"github.com/waitmoon/ice-server/storage"
)

type Scheduler struct {
	config        *config.Config
	serverService *ServerService
	clientManager *ClientManager
	storage       *storage.Storage
}

func NewScheduler(config *config.Config, serverService *ServerService, clientManager *ClientManager, store *storage.Storage) *Scheduler {
	return &Scheduler{config: config, serverService: serverService, clientManager: clientManager, storage: store}
}

func (s *Scheduler) Start() {
	// Cleanup: client + mock, runs at clientTimeout interval (30s)
	go func() {
		ticker := time.NewTicker(s.config.ClientTimeout)
		defer ticker.Stop()
		for range ticker.C {
			func() {
				defer func() {
					if r := recover(); r != nil {
						slog.Error("cleanup panicked", "recover", r)
					}
				}()
				s.clientManager.CleanInactiveClients()
				if err := s.storage.CleanStaleMocks(2 * time.Minute); err != nil {
					slog.Error("mock cleanup failed", "error", err)
				}
			}()
		}
	}()

	// Recycle task: daily at configured hour
	go func() {
		hour, minute := parseCronTime(s.config.RecycleCron)
		for {
			now := time.Now()
			next := time.Date(now.Year(), now.Month(), now.Day(), hour, minute, 0, 0, now.Location())
			if next.Before(now) {
				next = next.Add(24 * time.Hour)
			}
			sleepDur := next.Sub(now)
			time.Sleep(sleepDur)

			func() {
				defer func() {
					if r := recover(); r != nil {
						slog.Error("recycle panicked", "recover", r)
					}
				}()
				s.serverService.Recycle(nil)
			}()

			// Sleep a bit to avoid double trigger
			time.Sleep(time.Minute)
		}
	}()

	slog.Info("scheduler started", "cleanupInterval", s.config.ClientTimeout.String(), "recycleCron", s.config.RecycleCron)
}

// parseCronTime extracts hour and minute from a simple cron expression
// Supports: "M H * * *" format
func parseCronTime(cron string) (hour, minute int) {
	parts := strings.Fields(cron)
	if len(parts) >= 2 {
		minute, _ = strconv.Atoi(parts[0])
		hour, _ = strconv.Atoi(parts[1])
	} else {
		hour = 3
		minute = 0
	}
	return
}

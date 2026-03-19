package service

import (
	"log"
	"strconv"
	"strings"
	"time"

	"github.com/waitmoon/ice-server/config"
)

type Scheduler struct {
	config        *config.Config
	serverService *ServerService
	clientManager *ClientManager
}

func NewScheduler(config *config.Config, serverService *ServerService, clientManager *ClientManager) *Scheduler {
	return &Scheduler{config: config, serverService: serverService, clientManager: clientManager}
}

func (s *Scheduler) Start() {
	// Client cleanup: runs at clientTimeout interval
	go func() {
		ticker := time.NewTicker(s.config.ClientTimeout)
		defer ticker.Stop()
		for range ticker.C {
			func() {
				defer func() {
					if r := recover(); r != nil {
						log.Printf("client cleanup panic: %v", r)
					}
				}()
				s.clientManager.CleanInactiveClients()
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
						log.Printf("recycle panic: %v", r)
					}
				}()
				s.serverService.Recycle(nil)
			}()

			// Sleep a bit to avoid double trigger
			time.Sleep(time.Minute)
		}
	}()

	log.Printf("scheduler started: client cleanup every %v, recycle cron: %s", s.config.ClientTimeout, s.config.RecycleCron)
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

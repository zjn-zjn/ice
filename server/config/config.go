package config

import (
	"flag"
	"os"
	"strconv"
	"strings"
	"time"
)

type PublishTarget struct {
	Name string `json:"name"`
	URL  string `json:"url"`
}

type Config struct {
	Port               int
	StoragePath        string
	ClientTimeout      time.Duration
	VersionRetention   int
	RecycleCron        string
	RecycleWay         string
	RecycleProtectDays int
	Mode               string
	PublishTargets     []PublishTarget
}

func Load() *Config {
	cfg := &Config{
		Port:               8121,
		StoragePath:        "./ice-data",
		ClientTimeout:      30 * time.Second,
		VersionRetention:   1000,
		RecycleCron:        "0 3 * * *",
		RecycleWay:         "hard",
		RecycleProtectDays: 1,
		Mode:               "open",
	}

	flag.IntVar(&cfg.Port, "port", cfg.Port, "server port")
	flag.StringVar(&cfg.StoragePath, "storage-path", cfg.StoragePath, "storage path")
	flag.IntVar(&cfg.VersionRetention, "version-retention", cfg.VersionRetention, "version retention count")
	flag.StringVar(&cfg.RecycleCron, "recycle-cron", cfg.RecycleCron, "recycle cron expression")
	flag.StringVar(&cfg.RecycleWay, "recycle-way", cfg.RecycleWay, "recycle way: soft or hard")
	flag.IntVar(&cfg.RecycleProtectDays, "recycle-protect-days", cfg.RecycleProtectDays, "recycle protect days")
	flag.StringVar(&cfg.Mode, "mode", cfg.Mode, "server mode: open or controlled")

	var publishTargetsStr string
	flag.StringVar(&publishTargetsStr, "publish-targets", "", "publish targets: name1=url1,name2=url2")

	var clientTimeoutSec int
	flag.IntVar(&clientTimeoutSec, "client-timeout", 30, "client timeout in seconds")
	flag.Parse()

	cfg.ClientTimeout = time.Duration(clientTimeoutSec) * time.Second

	// Environment variables override flags
	if v := os.Getenv("ICE_PORT"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.Port = n
		}
	}
	if v := os.Getenv("ICE_STORAGE_PATH"); v != "" {
		cfg.StoragePath = v
	}
	if v := os.Getenv("ICE_CLIENT_TIMEOUT"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.ClientTimeout = time.Duration(n) * time.Second
		}
	}
	if v := os.Getenv("ICE_VERSION_RETENTION"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.VersionRetention = n
		}
	}
	if v := os.Getenv("ICE_RECYCLE_CRON"); v != "" {
		cfg.RecycleCron = v
	}
	if v := os.Getenv("ICE_RECYCLE_WAY"); v != "" {
		cfg.RecycleWay = v
	}
	if v := os.Getenv("ICE_RECYCLE_PROTECT_DAYS"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.RecycleProtectDays = n
		}
	}
	if v := os.Getenv("ICE_MODE"); v != "" {
		cfg.Mode = v
	}
	if v := os.Getenv("ICE_PUBLISH_TARGETS"); v != "" {
		publishTargetsStr = v
	}

	cfg.PublishTargets = parsePublishTargets(publishTargetsStr)

	return cfg
}

func parsePublishTargets(s string) []PublishTarget {
	if s == "" {
		return nil
	}
	var targets []PublishTarget
	for _, pair := range strings.Split(s, ",") {
		pair = strings.TrimSpace(pair)
		if idx := strings.Index(pair, "="); idx > 0 {
			targets = append(targets, PublishTarget{
				Name: strings.TrimSpace(pair[:idx]),
				URL:  strings.TrimSpace(pair[idx+1:]),
			})
		}
	}
	return targets
}

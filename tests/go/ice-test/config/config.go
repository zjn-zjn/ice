// Package config provides configuration loading for ice-test.
package config

import (
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// Config represents the application configuration.
type Config struct {
	Server ServerConfig `yaml:"server"`
	Ice    IceConfig    `yaml:"ice"`
}

// ServerConfig represents server configuration.
type ServerConfig struct {
	Port int `yaml:"port"`
}

// IceConfig represents ice client configuration.
type IceConfig struct {
	App               int           `yaml:"app"`
	Storage           StorageConfig `yaml:"storage"`
	PollInterval      int           `yaml:"poll-interval"`
	HeartbeatInterval int           `yaml:"heartbeat-interval"`
	Pool              PoolConfig    `yaml:"pool"`
}

// StorageConfig represents storage configuration.
type StorageConfig struct {
	Path string `yaml:"path"`
}

// PoolConfig represents thread pool configuration.
type PoolConfig struct {
	Parallelism int `yaml:"parallelism"`
}

// DefaultConfig returns the default configuration.
func DefaultConfig() *Config {
	return &Config{
		Server: ServerConfig{
			Port: 8084,
		},
		Ice: IceConfig{
			App: 1,
			Storage: StorageConfig{
				Path: "./ice-data",
			},
			PollInterval:      5,
			HeartbeatInterval: 10,
			Pool: PoolConfig{
				Parallelism: -1,
			},
		},
	}
}

// Load loads configuration from a YAML file.
func Load(path string) (*Config, error) {
	cfg := DefaultConfig()

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}

	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, err
	}

	return cfg, nil
}

// GetPollInterval returns poll interval as time.Duration.
func (c *Config) GetPollInterval() time.Duration {
	return time.Duration(c.Ice.PollInterval) * time.Second
}

// GetHeartbeatInterval returns heartbeat interval as time.Duration.
func (c *Config) GetHeartbeatInterval() time.Duration {
	return time.Duration(c.Ice.HeartbeatInterval) * time.Second
}


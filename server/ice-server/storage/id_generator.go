package storage

import (
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
)

// IceIdGenerator is a file-based atomic ID generator
type IceIdGenerator struct {
	filePath string
	mu       sync.Mutex
}

func NewIceIdGenerator(filePath string) *IceIdGenerator {
	return &IceIdGenerator{filePath: filePath}
}

func (g *IceIdGenerator) NextId() (int64, error) {
	g.mu.Lock()
	defer g.mu.Unlock()

	current, err := g.readCurrent()
	if err != nil {
		return 0, err
	}

	next := current + 1
	if err := g.writeTo(next); err != nil {
		return 0, err
	}
	return next, nil
}

func (g *IceIdGenerator) CurrentId() (int64, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	return g.readCurrent()
}

func (g *IceIdGenerator) EnsureNotLessThan(minId int64) error {
	g.mu.Lock()
	defer g.mu.Unlock()

	current, err := g.readCurrent()
	if err != nil {
		return err
	}
	if current < minId {
		return g.writeTo(minId)
	}
	return nil
}

func (g *IceIdGenerator) readCurrent() (int64, error) {
	data, err := os.ReadFile(g.filePath)
	if err != nil {
		if os.IsNotExist(err) {
			if err2 := os.MkdirAll(filepath.Dir(g.filePath), 0755); err2 != nil {
				return 0, err2
			}
			return 0, nil
		}
		return 0, err
	}
	content := strings.TrimSpace(string(data))
	if content == "" {
		return 0, nil
	}
	return strconv.ParseInt(content, 10, 64)
}

func (g *IceIdGenerator) writeTo(value int64) error {
	if err := os.MkdirAll(filepath.Dir(g.filePath), 0755); err != nil {
		return err
	}
	tmpPath := g.filePath + ".tmp"
	if err := os.WriteFile(tmpPath, []byte(strconv.FormatInt(value, 10)), 0644); err != nil {
		return err
	}
	return os.Rename(tmpPath, g.filePath)
}

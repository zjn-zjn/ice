package context

import (
	"strings"
	"time"

	"github.com/zjn-zjn/ice/sdks/go/internal/uuid"
)

// Meta holds ice execution metadata.
type Meta struct {
	Id      int64            `json:"id,omitempty"`
	Scene   string           `json:"scene,omitempty"`
	Nid     int64            `json:"nid,omitempty"`
	Ts      int64            `json:"ts"`
	Trace   string           `json:"trace,omitempty"`
	Debug   byte             `json:"debug,omitempty"`
	Process *strings.Builder `json:"-"`
}

// NewMeta creates a new Meta with default values.
func NewMeta() *Meta {
	return &Meta{
		Ts:      time.Now().UnixMilli(),
		Trace:   uuid.GenerateAlphanumId(11),
		Process: &strings.Builder{},
	}
}

package context

import (
	"encoding/json"
	"strings"
	"time"

	"github.com/zjn-zjn/ice/sdks/go/internal/uuid"
)

// IceMeta contains ice execution metadata, stored in Roam under the "_ice" key.
type IceMeta struct {
	Id      int64            `json:"id,omitempty"`
	Scene   string           `json:"scene,omitempty"`
	Nid     int64            `json:"nid,omitempty"`
	Ts      int64            `json:"ts,omitempty"`
	Trace   string           `json:"-"`
	Debug   byte             `json:"debug,omitempty"`
	Process *strings.Builder `json:"-"`
}

// MarshalJSON implements json.Marshaler for IceMeta.
func (m *IceMeta) MarshalJSON() ([]byte, error) {
	type Alias IceMeta
	return json.Marshal(&struct {
		*Alias
		Process string `json:"process,omitempty"`
	}{
		Alias:   (*Alias)(m),
		Process: m.Process.String(),
	})
}

// NewMeta creates a new IceMeta with default values.
func NewMeta() *IceMeta {
	return &IceMeta{
		Ts:      time.Now().UnixMilli(),
		Trace:   uuid.GenerateAlphanumId(11),
		Process: &strings.Builder{},
	}
}

// Clone creates a copy of the IceMeta with a fresh Process builder.
func (m *IceMeta) Clone() *IceMeta {
	return &IceMeta{
		Id:      m.Id,
		Scene:   m.Scene,
		Nid:     m.Nid,
		Ts:      m.Ts,
		Trace:   m.Trace,
		Debug:   m.Debug,
		Process: &strings.Builder{},
	}
}

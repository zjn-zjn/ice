package context

import (
	"encoding/json"
	"time"

	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/uuid"
)

// Pack represents the input for ice processing.
type Pack struct {
	// IceId is the handler/base ID to execute.
	IceId int64
	// Scene is the scene name for handler lookup.
	Scene string
	// ConfId is the specific conf node ID to execute.
	ConfId int64
	// Roam is the business data container.
	Roam *Roam
	// Type is the request type.
	Type enum.RequestType
	// RequestTime is the request timestamp in milliseconds.
	RequestTime int64
	// TraceId is the trace ID for logging.
	TraceId string
	// Priority is the execution priority.
	Priority int64
	// Debug controls debug output.
	// 1: IN_PACK, 2: PROCESS, 4: OUT_ROAM, 8: OUT_PACK
	Debug byte
}

// NewPack creates a new Pack with default values.
func NewPack() *Pack {
	return &Pack{
		Roam:        NewRoam(),
		Type:        enum.RequestFormal,
		RequestTime: time.Now().UnixMilli(),
		TraceId:     uuid.Generate22(),
	}
}

// NewPackWithTraceId creates a new Pack with a specific trace ID.
func NewPackWithTraceId(traceId string) *Pack {
	p := NewPack()
	if traceId != "" {
		p.TraceId = traceId
	}
	return p
}

// SetIceId sets the ice ID and returns the Pack for chaining.
func (p *Pack) SetIceId(iceId int64) *Pack {
	p.IceId = iceId
	return p
}

// SetScene sets the scene and returns the Pack for chaining.
func (p *Pack) SetScene(scene string) *Pack {
	p.Scene = scene
	return p
}

// SetConfId sets the conf ID and returns the Pack for chaining.
func (p *Pack) SetConfId(confId int64) *Pack {
	p.ConfId = confId
	return p
}

// SetRoam sets the roam and returns the Pack for chaining.
func (p *Pack) SetRoam(roam *Roam) *Pack {
	p.Roam = roam
	return p
}

// SetDebug sets the debug flag and returns the Pack for chaining.
func (p *Pack) SetDebug(debug byte) *Pack {
	p.Debug = debug
	return p
}

// SetPriority sets the priority and returns the Pack for chaining.
func (p *Pack) SetPriority(priority int64) *Pack {
	p.Priority = priority
	return p
}

// Clone creates a shallow copy of the Pack with a new Roam.
func (p *Pack) Clone() *Pack {
	return &Pack{
		IceId:       p.IceId,
		Scene:       p.Scene,
		ConfId:      p.ConfId,
		Roam:        p.Roam.ShallowCopy(),
		Type:        p.Type,
		RequestTime: p.RequestTime,
		TraceId:     p.TraceId,
		Priority:    p.Priority,
		Debug:       p.Debug,
	}
}

// String returns the JSON representation of the Pack.
func (p *Pack) String() string {
	b, err := json.Marshal(p)
	if err != nil {
		return "{}"
	}
	return string(b)
}

// MarshalJSON implements json.Marshaler with Roam data included.
func (p *Pack) MarshalJSON() ([]byte, error) {
	type Alias Pack
	return json.Marshal(&struct {
		*Alias
		Roam map[string]any `json:"Roam,omitempty"`
	}{
		Alias: (*Alias)(p),
		Roam:  p.Roam.Data(),
	})
}

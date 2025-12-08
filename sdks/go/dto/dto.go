// Package dto defines data transfer objects compatible with Java JSON serialization.
package dto

// ConfDto represents the configuration of a node (compatible with Java IceConfDto).
type ConfDto struct {
	Id         int64  `json:"id"`
	SonIds     string `json:"sonIds,omitempty"`
	Type       byte   `json:"type"`
	ConfName   string `json:"confName,omitempty"`
	ConfField  string `json:"confField,omitempty"`
	TimeType   byte   `json:"timeType,omitempty"`
	Start      int64  `json:"start,omitempty"`
	End        int64  `json:"end,omitempty"`
	ForwardId  int64  `json:"forwardId,omitempty"`
	Debug      byte   `json:"debug,omitempty"`
	ErrorState byte   `json:"errorState,omitempty"`
	Inverse    bool   `json:"inverse,omitempty"`
	Name       string `json:"name,omitempty"`
	App        int    `json:"app,omitempty"`
	Status     byte   `json:"status,omitempty"`
	CreateAt   int64  `json:"createAt,omitempty"`
	UpdateAt   int64  `json:"updateAt,omitempty"`
	// Only in ice_conf_update
	IceId  int64 `json:"iceId,omitempty"`
	ConfId int64 `json:"confId,omitempty"`
}

// BaseDto represents a base/handler configuration (compatible with Java IceBaseDto).
type BaseDto struct {
	Id       int64  `json:"id"`
	Scenes   string `json:"scenes,omitempty"`
	ConfId   int64  `json:"confId,omitempty"`
	TimeType byte   `json:"timeType,omitempty"`
	Start    int64  `json:"start,omitempty"`
	End      int64  `json:"end,omitempty"`
	Debug    byte   `json:"debug,omitempty"`
	Priority int64  `json:"priority,omitempty"`
	App      int    `json:"app,omitempty"`
	Name     string `json:"name,omitempty"`
	Status   byte   `json:"status,omitempty"`
	CreateAt int64  `json:"createAt,omitempty"`
	UpdateAt int64  `json:"updateAt,omitempty"`
}

// TransferDto represents data transfer for configuration updates (compatible with Java IceTransferDto).
type TransferDto struct {
	Version             int64     `json:"version"`
	InsertOrUpdateConfs []ConfDto `json:"insertOrUpdateConfs,omitempty"`
	DeleteConfIds       []int64   `json:"deleteConfIds,omitempty"`
	InsertOrUpdateBases []BaseDto `json:"insertOrUpdateBases,omitempty"`
	DeleteBaseIds       []int64   `json:"deleteBaseIds,omitempty"`
}

// ClientInfo represents client information (compatible with Java IceClientInfo).
type ClientInfo struct {
	Address       string         `json:"address"`
	App           int            `json:"app"`
	LeafNodes     []LeafNodeInfo `json:"leafNodes,omitempty"`
	LastHeartbeat int64          `json:"lastHeartbeat"`
	StartTime     int64          `json:"startTime"`
	LoadedVersion int64          `json:"loadedVersion"`
}

// LeafNodeInfo represents information about a leaf node class.
type LeafNodeInfo struct {
	Type       byte           `json:"type"`
	Clazz      string         `json:"clazz"`
	Name       string         `json:"name,omitempty"`
	Desc       string         `json:"desc,omitempty"`
	Order      int            `json:"order,omitempty"`
	IceFields  []IceFieldInfo `json:"iceFields,omitempty"`
	HideFields []IceFieldInfo `json:"hideFields,omitempty"`
}

// IceFieldInfo represents information about a field in a leaf node.
type IceFieldInfo struct {
	Field     string `json:"field"`
	Name      string `json:"name,omitempty"`
	Desc      string `json:"desc,omitempty"`
	Type      string `json:"type,omitempty"`
	Value     any    `json:"value,omitempty"`
	ValueNull bool   `json:"valueNull,omitempty"`
}

// AppDto represents an application (compatible with Java IceAppDto).
type AppDto struct {
	Id       int64  `json:"id"`
	Name     string `json:"name"`
	Secret   string `json:"secret,omitempty"`
	CreateAt int64  `json:"createAt,omitempty"`
	UpdateAt int64  `json:"updateAt,omitempty"`
}

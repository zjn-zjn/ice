package model

import (
	"strconv"
	"strings"
	"time"
)

// Status constants (matching IceStorageConstants)
const (
	StatusOnline  int8 = 1
	StatusOffline int8 = 0
	StatusDeleted int8 = -1
)

// NodeType constants (matching NodeTypeEnum)
const (
	NodeTypeNone     int8 = 0
	NodeTypeAnd      int8 = 1
	NodeTypeTrue     int8 = 2
	NodeTypeAll      int8 = 3
	NodeTypeAny      int8 = 4
	NodeTypeFlow     int8 = 5
	NodeTypeResult   int8 = 6
	NodeTypeLeafNone int8 = 7
	NodeTypePNone    int8 = 8
	NodeTypePAnd     int8 = 9
	NodeTypePTrue    int8 = 10
	NodeTypePAll     int8 = 11
	NodeTypePAny     int8 = 12
)

// TimeType constants (matching TimeTypeEnum)
const (
	TimeTypeNone       int8 = 1
	TimeTypeAfterStart int8 = 5
	TimeTypeBeforeEnd  int8 = 6
	TimeTypeBetween    int8 = 7
)

// EditType constants (matching EditTypeEnum)
const (
	EditTypeAddSon     = 1
	EditTypeEdit       = 2
	EditTypeDelete     = 3
	EditTypeAddForward = 4
	EditTypeExchange   = 5
	EditTypeMove       = 6
)

var nodeTypeNames = map[int8]string{
	0: "NONE", 1: "AND", 2: "TRUE", 3: "ALL", 4: "ANY",
	5: "LEAF_FLOW", 6: "LEAF_RESULT", 7: "LEAF_NONE",
	8: "P_NONE", 9: "P_AND", 10: "P_TRUE", 11: "P_ALL", 12: "P_ANY",
}

func NodeTypeName(t int8) string {
	if name, ok := nodeTypeNames[t]; ok {
		return name
	}
	return "UNKNOWN"
}

func IsRelation(nodeType int8) bool {
	return nodeType >= 0 && nodeType <= 4 || nodeType >= 8 && nodeType <= 12
}

func IsLeaf(nodeType int8) bool {
	return nodeType >= 5 && nodeType <= 7
}

func IsValidTimeType(t int8) bool {
	return t == TimeTypeNone || t == TimeTypeAfterStart || t == TimeTypeBeforeEnd || t == TimeTypeBetween
}

func IsValidEditType(t int) bool {
	return t >= EditTypeAddSon && t <= EditTypeMove
}

// TimeNowMs returns current time in milliseconds
func TimeNowMs() int64 {
	return time.Now().UnixMilli()
}

// ---- Data Models (matching Java DTOs, stored as JSON) ----

type IceApp struct {
	ID       *int    `json:"id,omitempty"`
	Name     string  `json:"name,omitempty"`
	Info     string  `json:"info,omitempty"`
	Status   *int8   `json:"status,omitempty"`
	CreateAt *int64  `json:"createAt,omitempty"`
	UpdateAt *int64  `json:"updateAt,omitempty"`
}

type IceBase struct {
	ID       *int64  `json:"id,omitempty"`
	Name     string  `json:"name,omitempty"`
	App      *int    `json:"app,omitempty"`
	Scenes   string  `json:"scenes,omitempty"`
	Status   *int8   `json:"status,omitempty"`
	ConfID   *int64  `json:"confId,omitempty"`
	TimeType *int8   `json:"timeType,omitempty"`
	Start    *int64  `json:"start,omitempty"`
	End      *int64  `json:"end,omitempty"`
	Debug    *int8   `json:"debug,omitempty"`
	CreateAt *int64  `json:"createAt,omitempty"`
	UpdateAt *int64  `json:"updateAt,omitempty"`
}

type IceConf struct {
	ID         int64  `json:"id"`
	App        int    `json:"app,omitempty"`
	Name       string `json:"name,omitempty"`
	SonIds     string `json:"sonIds,omitempty"`
	Type       int8   `json:"type"`
	Status     *int8  `json:"status,omitempty"`
	Inverse    *bool  `json:"inverse,omitempty"`
	ConfName   string `json:"confName,omitempty"`
	ConfField  string `json:"confField,omitempty"`
	ForwardId  *int64 `json:"forwardId,omitempty"`
	TimeType   *int8  `json:"timeType,omitempty"`
	Start      *int64 `json:"start,omitempty"`
	End        *int64 `json:"end,omitempty"`
	ErrorState *int8  `json:"errorState,omitempty"`
	CreateAt   *int64 `json:"createAt,omitempty"`
	UpdateAt   *int64 `json:"updateAt,omitempty"`
	IceId      *int64 `json:"iceId,omitempty"`
	ConfId     *int64 `json:"confId,omitempty"`
}

func (c *IceConf) GetMixId() int64 {
	if c.ConfId != nil {
		return *c.ConfId
	}
	return c.ID
}

func (c *IceConf) IsUpdating() bool {
	return c.ConfId != nil
}

func (c *IceConf) GetSonLongIds() []int64 {
	if !IsRelation(c.Type) || c.SonIds == "" {
		return nil
	}
	parts := strings.Split(c.SonIds, ",")
	ids := make([]int64, 0, len(parts))
	for _, p := range parts {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		id, err := strconv.ParseInt(p, 10, 64)
		if err == nil {
			ids = append(ids, id)
		}
	}
	return ids
}

func (c *IceConf) GetSonLongIdSet() map[int64]bool {
	ids := c.GetSonLongIds()
	if ids == nil {
		return nil
	}
	set := make(map[int64]bool, len(ids))
	for _, id := range ids {
		set[id] = true
	}
	return set
}

func (c *IceConf) IsInverse() bool {
	return c.Inverse != nil && *c.Inverse
}

func (c *IceConf) GetTimeType() int8 {
	if c.TimeType != nil {
		return *c.TimeType
	}
	return TimeTypeNone
}

// IceEditNode represents an edit operation from the frontend
type IceEditNode struct {
	App            *int    `json:"app"`
	EditType       *int    `json:"editType"`
	IceId          *int64  `json:"iceId"`
	SelectId       *int64  `json:"selectId"`
	Name           string  `json:"name,omitempty"`
	ConfField      string  `json:"confField,omitempty"`
	NodeType       *int8   `json:"nodeType,omitempty"`
	ConfName       string  `json:"confName,omitempty"`
	MultiplexIds   string  `json:"multiplexIds,omitempty"`
	TimeType       *int8   `json:"timeType,omitempty"`
	Start          *int64  `json:"start,omitempty"`
	End            *int64  `json:"end,omitempty"`
	ParentId       *int64  `json:"parentId,omitempty"`
	Index          *int    `json:"index,omitempty"`
	MoveTo         *int    `json:"moveTo,omitempty"`
	MoveToParentId *int64  `json:"moveToParentId,omitempty"`
	MoveToNextId   *int64  `json:"moveToNextId,omitempty"`
	NextId         *int64  `json:"nextId,omitempty"`
	Inverse        *bool   `json:"inverse,omitempty"`
	Lane           string  `json:"lane,omitempty"`
}

// EditConfResponse is the response for editConf API
type EditConfResponse struct {
	NodeId int64          `json:"nodeId"`
	Nodes  []*IceShowNode `json:"nodes,omitempty"`
}

func (e *IceEditNode) GetTimeType() int8 {
	if e.TimeType != nil {
		return *e.TimeType
	}
	return TimeTypeNone
}

func (e *IceEditNode) IsInverse() bool {
	return e.Inverse != nil && *e.Inverse
}

// PushData is the snapshot data for push/export/import
type PushData struct {
	App         int        `json:"app,omitempty"`
	Base        *IceBase   `json:"base,omitempty"`
	Confs       []*IceConf `json:"confs,omitempty"`
	ConfUpdates []*IceConf `json:"confUpdates,omitempty"`
}

// IcePushHistory is a push history record
type IcePushHistory struct {
	ID       *int64 `json:"id,omitempty"`
	App      int    `json:"app,omitempty"`
	IceId    int64  `json:"iceId,omitempty"`
	Reason   string `json:"reason,omitempty"`
	Operator string `json:"operator,omitempty"`
	CreateAt *int64 `json:"createAt,omitempty"`
	PushData string `json:"pushData,omitempty"`
}

// IceLeafClass represents a leaf node class for the UI
type IceLeafClass struct {
	FullName string `json:"fullName"`
	Name     string `json:"name"`
	Order    int    `json:"order"`
}

// IceBaseSearch is the search criteria for base list
type IceBaseSearch struct {
	App      int
	BaseId   *int64
	Name     string
	Scene    string
	PageNum  int
	PageSize int
}

// IceBaseCreate extends IceBase with an optional folder path for creation
type IceBaseCreate struct {
	IceBase
	Path string `json:"path,omitempty"` // folder path relative to bases/, empty = root
}

// IceTransferDto is the version update data
type IceTransferDto struct {
	Version             int64      `json:"version"`
	InsertOrUpdateConfs []*IceConf `json:"insertOrUpdateConfs,omitempty"`
	DeleteConfIds       []int64    `json:"deleteConfIds,omitempty"`
	InsertOrUpdateBases []*IceBase `json:"insertOrUpdateBases,omitempty"`
	DeleteBaseIds       []int64    `json:"deleteBaseIds,omitempty"`
}

// IceClientInfo represents a registered client
type IceClientInfo struct {
	Address       string          `json:"address"`
	App           int             `json:"app"`
	Lane          string          `json:"lane,omitempty"`
	LeafNodes     []*LeafNodeInfo `json:"leafNodes,omitempty"`
	LastHeartbeat *int64          `json:"lastHeartbeat,omitempty"`
	StartTime     *int64          `json:"startTime,omitempty"`
	LoadedVersion *int64          `json:"loadedVersion,omitempty"`
}

// LeafNodeInfo describes a leaf node class from a client
type LeafNodeInfo struct {
	Type       int8             `json:"type"`
	Clazz      string           `json:"clazz,omitempty"`
	Name       string           `json:"name,omitempty"`
	Desc       string           `json:"desc,omitempty"`
	Order      *int             `json:"order,omitempty"`
	IceFields  []*IceFieldInfo  `json:"iceFields,omitempty"`
	HideFields []*IceFieldInfo  `json:"hideFields,omitempty"`
	RoamKeys   []*RoamKeyMeta   `json:"roamKeys,omitempty"`
}

// RoamKeyMeta describes a roam key access found in a leaf node.
type RoamKeyMeta struct {
	Direction    string     `json:"direction"`
	AccessMode   string     `json:"accessMode"`
	AccessMethod string     `json:"accessMethod"`
	KeyParts     []*KeyPart `json:"keyParts,omitempty"`
}

// KeyPart describes one segment of a roam key.
type KeyPart struct {
	Type    string     `json:"type"`
	Value   string     `json:"value,omitempty"`
	Ref     string     `json:"ref,omitempty"`
	FromKey string     `json:"fromKey,omitempty"`
	Parts   []*KeyPart `json:"parts,omitempty"`
}

func (n *LeafNodeInfo) GetOrder() int {
	if n.Order != nil {
		return *n.Order
	}
	return 100
}

// IceFieldInfo describes a field of a leaf node
type IceFieldInfo struct {
	Field     string      `json:"field,omitempty"`
	Name      string      `json:"name,omitempty"`
	Desc      string      `json:"desc,omitempty"`
	Type      string      `json:"type,omitempty"`
	Value     any `json:"value"`
	ValueNull *bool       `json:"valueNull,omitempty"`
}

// ---- Display Models ----

// IceShowConf is the top-level display model for conf detail
type IceShowConf struct {
	App            int                              `json:"app,omitempty"`
	IceId          int64                            `json:"iceId,omitempty"`
	Name           string                           `json:"name,omitempty"`
	Root           *IceShowNode                     `json:"root,omitempty"`
	UpdateCount    *int                             `json:"updateCount,omitempty"`
	ClientRegistry *ClientRegistryInfo              `json:"clientRegistry,omitempty"`
	LeafClassMap   map[int8][]*LeafNodeInfo          `json:"leafClassMap,omitempty"`
}

type ClientRegistryInfo struct {
	MainClients []*ShowClientInfo            `json:"mainClients,omitempty"`
	LaneClients map[string][]*ShowClientInfo `json:"laneClients,omitempty"`
}

type ShowClientInfo struct {
	Address string `json:"address"`
}

// IceShowNode represents a node in the display tree
type IceShowNode struct {
	ShowConf  *NodeShowConf  `json:"showConf,omitempty"`
	Forward   *IceShowNode   `json:"forward,omitempty"`
	Children  []*IceShowNode `json:"children,omitempty"`
	Start     *int64         `json:"start,omitempty"`
	End       *int64         `json:"end,omitempty"`
	ParentId  *int64         `json:"parentId,omitempty"`
	NextId    *int64         `json:"nextId,omitempty"`
	Index     *int           `json:"index,omitempty"`
	SonIds    string         `json:"sonIds,omitempty"`
	ForwardId *int64         `json:"forwardId,omitempty"`
	TimeType  *int8          `json:"timeType,omitempty"`
}

type NodeShowConf struct {
	UniqueKey       string        `json:"uniqueKey,omitempty"`
	NodeId          int64         `json:"nodeId,omitempty"`
	ErrorState      *int8         `json:"errorState,omitempty"`
	Inverse         *bool         `json:"inverse,omitempty"`
	NodeType        *int8         `json:"nodeType,omitempty"`
	NodeName        string        `json:"nodeName,omitempty"`
	LabelName       string        `json:"labelName,omitempty"`
	ConfName        string        `json:"confName,omitempty"`
	ConfField       string        `json:"confField,omitempty"`
	Updating        *bool         `json:"updating,omitempty"`
	HaveMeta        *bool         `json:"haveMeta,omitempty"`
	NodeInfo        *LeafNodeInfo `json:"nodeInfo,omitempty"`
	ClassRegistered *bool         `json:"classRegistered,omitempty"`
}

// ---- Helper functions for pointer creation ----

func Int8Ptr(v int8) *int8     { return &v }
func Int64Ptr(v int64) *int64  { return &v }
func IntPtr(v int) *int        { return &v }
func BoolPtr(v bool) *bool     { return &v }

// ClientHeartbeat is the lightweight heartbeat data written separately from meta.
type ClientHeartbeat struct {
	LastHeartbeat *int64 `json:"lastHeartbeat,omitempty"`
	LoadedVersion *int64 `json:"loadedVersion,omitempty"`
}

// ---- Mock Execution Models ----

// MockRequest is the request from server to client for mock execution.
type MockRequest struct {
	MockId   string         `json:"mockId"`
	App      int            `json:"app"`
	IceId    int64          `json:"iceId,omitempty"`
	ConfId   int64          `json:"confId,omitempty"`
	Scene    string         `json:"scene,omitempty"`
	Ts       int64          `json:"ts,omitempty"`
	Debug    byte           `json:"debug,omitempty"`
	Roam     map[string]any `json:"roam,omitempty"`
	CreateAt int64          `json:"createAt"`
}

// MockResult is the result from client to server after mock execution.
type MockResult struct {
	MockId    string         `json:"mockId"`
	Success   bool           `json:"success"`
	Roam      map[string]any `json:"roam,omitempty"`
	Trace     string         `json:"trace,omitempty"`
	Ts        int64          `json:"ts,omitempty"`
	Process   string         `json:"process,omitempty"`
	Error     string         `json:"error,omitempty"`
	Fallback  bool           `json:"fallback,omitempty"`
	ExecuteAt int64          `json:"executeAt"`
}

// MockExecuteRequest is the API input from frontend to server.
type MockExecuteRequest struct {
	App    int            `json:"app"`
	IceId  int64          `json:"iceId,omitempty"`
	ConfId int64          `json:"confId,omitempty"`
	Scene  string         `json:"scene,omitempty"`
	Ts     int64          `json:"ts,omitempty"`
	Debug  byte           `json:"debug,omitempty"`
	Roam   map[string]any `json:"roam,omitempty"`
	Target string         `json:"target"`
}

// MockSchemaField describes a roam input field for mock execution.
type MockSchemaField struct {
	Key    string `json:"key"`
	NodeId int64  `json:"nodeId"`
	NodeName  string `json:"nodeName,omitempty"`
	Dynamic   bool   `json:"dynamic,omitempty"`
}

// MockSchemaResponse wraps schema fields with fallback info.
type MockSchemaResponse struct {
	Fields   []*MockSchemaField `json:"fields"`
	Fallback bool               `json:"fallback,omitempty"` // true if target address/lane was offline and fell back
}

// Package enum defines enumerations used throughout the ice framework.
package enum

// RunState represents the execution result of a node.
type RunState byte

const (
	// FALSE indicates the node returned false.
	FALSE RunState = 0
	// TRUE indicates the node returned true.
	TRUE RunState = 1
	// NONE indicates the node has no result (neutral).
	NONE RunState = 2
	// SHUT_DOWN indicates the node should stop execution.
	SHUT_DOWN RunState = 3
)

// String returns the string representation of RunState.
func (s RunState) String() string {
	switch s {
	case FALSE:
		return "FALSE"
	case TRUE:
		return "TRUE"
	case NONE:
		return "NONE"
	case SHUT_DOWN:
		return "SHUT_DOWN"
	default:
		return "UNKNOWN"
	}
}

// NodeType represents the type of a node.
type NodeType byte

const (
	TypeNone       NodeType = 0
	TypeAnd        NodeType = 1
	TypeTrue       NodeType = 2
	TypeAll        NodeType = 3
	TypeAny        NodeType = 4
	TypeLeafFlow   NodeType = 5
	TypeLeafResult NodeType = 6
	TypeLeafNone   NodeType = 7
	TypePNone      NodeType = 8
	TypePAnd       NodeType = 9
	TypePTrue      NodeType = 10
	TypePAll       NodeType = 11
	TypePAny       NodeType = 12
)

// IsRelation returns true if the node type is a relation node.
func (t NodeType) IsRelation() bool {
	switch t {
	case TypeNone, TypeAnd, TypeTrue, TypeAll, TypeAny,
		TypePNone, TypePAnd, TypePTrue, TypePAll, TypePAny:
		return true
	default:
		return false
	}
}

// IsLeaf returns true if the node type is a leaf node.
func (t NodeType) IsLeaf() bool {
	return t == TypeLeafFlow || t == TypeLeafResult || t == TypeLeafNone
}

// TimeType represents the time constraint type for a node.
type TimeType byte

const (
	// TimeNone means no time constraint.
	TimeNone TimeType = 1
	// TimeAfterStart means only execute after the start time.
	TimeAfterStart TimeType = 5
	// TimeBeforeEnd means only execute before the end time.
	TimeBeforeEnd TimeType = 6
	// TimeBetween means only execute between start and end time.
	TimeBetween TimeType = 7
)

// RequestType represents the type of request.
type RequestType int

const (
	// RequestFormal is a formal/production request.
	RequestFormal RequestType = 0
)

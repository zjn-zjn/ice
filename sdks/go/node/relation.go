package node

import (
	"github.com/waitmoon/ice/sdks/go/internal/linkedlist"
)

// Relation is the base type for relation nodes that contain children.
type Relation struct {
	Base
	Children *linkedlist.LinkedList[Node]
	SonIds   []int64
}

// NewRelation creates a new Relation.
func NewRelation() *Relation {
	return &Relation{
		Children: linkedlist.New[Node](),
		SonIds:   make([]int64, 0),
	}
}

// GetChildren returns the children list.
func (r *Relation) GetChildren() *linkedlist.LinkedList[Node] {
	return r.Children
}

// SetChildren sets the children list.
func (r *Relation) SetChildren(children *linkedlist.LinkedList[Node]) {
	r.Children = children
}

// GetSonIds returns the son IDs.
func (r *Relation) GetSonIds() []int64 {
	return r.SonIds
}

// SetSonIds sets the son IDs.
func (r *Relation) SetSonIds(ids []int64) {
	r.SonIds = ids
}

// RelationNode is an interface for relation nodes.
type RelationNode interface {
	Node
	BaseAccessor
	GetChildren() *linkedlist.LinkedList[Node]
	SetChildren(*linkedlist.LinkedList[Node])
	GetSonIds() []int64
	SetSonIds([]int64)
}

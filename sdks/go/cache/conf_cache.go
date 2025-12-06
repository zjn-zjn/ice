// Package cache provides caching for configurations and handlers.
package cache

import (
	"context"
	"strconv"
	"strings"
	"sync"

	"github.com/waitmoon/ice/sdks/go/dto"
	"github.com/waitmoon/ice/sdks/go/enum"
	"github.com/waitmoon/ice/sdks/go/internal/linkedlist"
	"github.com/waitmoon/ice/sdks/go/leaf"
	"github.com/waitmoon/ice/sdks/go/log"
	"github.com/waitmoon/ice/sdks/go/node"
	"github.com/waitmoon/ice/sdks/go/relation"
	"github.com/waitmoon/ice/sdks/go/relation/parallel"
)

var (
	confMap          = make(map[int64]node.Node)
	parentIdsMap     = make(map[int64]map[int64]struct{})
	forwardUseIdsMap = make(map[int64]map[int64]struct{})
	confMu           sync.RWMutex
)

// GetConfById returns a node by conf ID.
func GetConfById(id int64) node.Node {
	confMu.RLock()
	defer confMu.RUnlock()
	return confMap[id]
}

// GetConfMap returns a copy of the conf map.
func GetConfMap() map[int64]node.Node {
	confMu.RLock()
	defer confMu.RUnlock()
	result := make(map[int64]node.Node, len(confMap))
	for k, v := range confMap {
		result[k] = v
	}
	return result
}

// InsertOrUpdateConfs inserts or updates configurations.
func InsertOrUpdateConfs(confDtos []dto.ConfDto) []string {
	var errors []string
	tmpConfMap := make(map[int64]node.Node, len(confDtos))

	// First pass: create all nodes
	for _, confDto := range confDtos {
		n, err := convertNode(&confDto)
		if err != nil {
			errors = append(errors, err.Error())
			log.Error(context.Background(), "failed to convert node", "error", err, "confId", confDto.Id)
			continue
		}
		tmpConfMap[confDto.Id] = n
	}

	confMu.Lock()
	defer confMu.Unlock()

	// Second pass: set up relationships
	for _, confDto := range confDtos {
		// Handle relation nodes
		if enum.NodeType(confDto.Type).IsRelation() {
			var sonIds []int64
			if confDto.SonIds != "" {
				sonIdStrs := strings.Split(confDto.SonIds, ",")
				sonIds = make([]int64, 0, len(sonIdStrs))
				for _, s := range sonIdStrs {
					id, _ := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
					sonIds = append(sonIds, id)
				}
			}

			tmpNode := tmpConfMap[confDto.Id]
			if tmpNode != nil && len(sonIds) > 0 {
				setRelationSonIds(tmpNode, sonIds)
				children := linkedlist.New[node.Node]()

				for _, sonId := range sonIds {
					// Track parent-child relationships
					if parentIdsMap[sonId] == nil {
						parentIdsMap[sonId] = make(map[int64]struct{})
					}
					parentIdsMap[sonId][confDto.Id] = struct{}{}

					// Find child node
					child := tmpConfMap[sonId]
					if child == nil {
						child = confMap[sonId]
					}
					if child == nil {
						errors = append(errors, "sonId not exist: "+strconv.FormatInt(sonId, 10))
						log.Error(context.Background(), "sonId not exist", "sonId", sonId)
					} else {
						children.Add(child)
					}
				}
				setRelationChildren(tmpNode, children)
			}
		}

		// Handle forward nodes
		if confDto.ForwardId > 0 {
			if forwardUseIdsMap[confDto.ForwardId] == nil {
				forwardUseIdsMap[confDto.ForwardId] = make(map[int64]struct{})
			}
			forwardUseIdsMap[confDto.ForwardId][confDto.Id] = struct{}{}

			forwardNode := tmpConfMap[confDto.ForwardId]
			if forwardNode == nil {
				forwardNode = confMap[confDto.ForwardId]
			}
			if forwardNode == nil {
				errors = append(errors, "forwardId not exist: "+strconv.FormatInt(confDto.ForwardId, 10))
				log.Error(context.Background(), "forwardId not exist", "forwardId", confDto.ForwardId)
			} else {
				setForward(tmpConfMap[confDto.Id], forwardNode)
			}
		}
	}

	// Merge into main map
	for id, n := range tmpConfMap {
		confMap[id] = n
	}

	// Update handler roots
	for _, confDto := range confDtos {
		tmpNode := confMap[confDto.Id]
		if tmpNode != nil {
			UpdateHandlerRoot(tmpNode)
		}
	}

	return errors
}

// DeleteConfs removes configurations by IDs.
func DeleteConfs(ids []int64) {
	confMu.Lock()
	defer confMu.Unlock()

	for _, id := range ids {
		delete(confMap, id)
	}
}

func convertNode(confDto *dto.ConfDto) (node.Node, error) {
	nodeType := enum.NodeType(confDto.Type)

	var n node.Node
	switch nodeType {
	case enum.TypeNone:
		r := relation.NewNone()
		r.IceLogName = "None"
		n = r
	case enum.TypeAnd:
		r := relation.NewAnd()
		r.IceLogName = "And"
		n = r
	case enum.TypeTrue:
		r := relation.NewTrue()
		r.IceLogName = "True"
		n = r
	case enum.TypeAll:
		r := relation.NewAll()
		r.IceLogName = "All"
		n = r
	case enum.TypeAny:
		r := relation.NewAny()
		r.IceLogName = "Any"
		n = r
	case enum.TypePNone:
		r := parallel.NewNone()
		r.IceLogName = "P-None"
		n = r
	case enum.TypePAnd:
		r := parallel.NewAnd()
		r.IceLogName = "P-And"
		n = r
	case enum.TypePTrue:
		r := parallel.NewTrue()
		r.IceLogName = "P-True"
		n = r
	case enum.TypePAll:
		r := parallel.NewAll()
		r.IceLogName = "P-All"
		n = r
	case enum.TypePAny:
		r := parallel.NewAny()
		r.IceLogName = "P-Any"
		n = r
	case enum.TypeLeafFlow, enum.TypeLeafResult, enum.TypeLeafNone:
		leafNode, err := leaf.CreateNode(confDto.ConfName, confDto.ConfField)
		if err != nil {
			return nil, err
		}
		n = leafNode
	default:
		// Try to create as leaf
		if confDto.ConfName != "" {
			leafNode, err := leaf.CreateNode(confDto.ConfName, confDto.ConfField)
			if err != nil {
				return nil, err
			}
			n = leafNode
		}
	}

	// Set common properties
	if n != nil {
		setNodeProperties(n, confDto)
	}
	return n, nil
}

func setNodeProperties(n node.Node, confDto *dto.ConfDto) {
	switch v := n.(type) {
	case node.RelationNode:
		setBaseProperties(v.GetBase(), confDto)
	case leaf.LeafNode:
		base := &node.Base{}
		setBaseProperties(base, confDto)
		base.IceLogName = getSimpleName(confDto.ConfName)
		v.SetBase(base)
	}
}

func setBaseProperties(base *node.Base, confDto *dto.ConfDto) {
	base.IceNodeId = confDto.Id
	base.IceNodeDebug = confDto.Debug == 0 || confDto.Debug == 1
	base.IceInverse = confDto.Inverse
	base.IceTimeType = enum.TimeType(confDto.TimeType)
	base.IceStart = confDto.Start
	base.IceEnd = confDto.End
	if confDto.ErrorState != 0 {
		state := enum.RunState(confDto.ErrorState)
		base.IceErrorState = &state
	}
	base.IceType = enum.NodeType(confDto.Type)
}

func setForward(n node.Node, forward node.Node) {
	if ba, ok := n.(node.BaseAccessor); ok {
		ba.SetForward(forward)
	}
}

func setRelationChildren(n node.Node, children *linkedlist.LinkedList[node.Node]) {
	if rn, ok := n.(node.RelationNode); ok {
		rn.SetChildren(children)
	}
}

func setRelationSonIds(n node.Node, sonIds []int64) {
	if rn, ok := n.(node.RelationNode); ok {
		rn.SetSonIds(sonIds)
	}
}

// getSimpleName extracts the simple class name from a fully qualified name.
// e.g., "com.example.ScoreFlow" -> "ScoreFlow"
func getSimpleName(fullName string) string {
	if fullName == "" {
		return ""
	}
	if idx := strings.LastIndex(fullName, "."); idx >= 0 {
		return fullName[idx+1:]
	}
	return fullName
}

// Package cache provides caching for configurations and handlers.
package cache

import (
	"context"
	"strconv"
	"strings"
	"sync"

	"github.com/zjn-zjn/ice/sdks/go/dto"
	"github.com/zjn-zjn/ice/sdks/go/enum"
	"github.com/zjn-zjn/ice/sdks/go/internal/linkedlist"
	"github.com/zjn-zjn/ice/sdks/go/leaf"
	"github.com/zjn-zjn/ice/sdks/go/log"
	"github.com/zjn-zjn/ice/sdks/go/node"
	"github.com/zjn-zjn/ice/sdks/go/relation"
	"github.com/zjn-zjn/ice/sdks/go/relation/parallel"
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
// This method mirrors Java's IceConfCache.insertOrUpdate logic for consistency.
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

	// Second pass: set up relationships and handle cleanup of old relationships
	for _, confDto := range confDtos {
		origin := confMap[confDto.Id]
		isRelation := enum.NodeType(confDto.Type).IsRelation()

		// Parse new sonIds
		var sonIds []int64
		var sonIdSet = make(map[int64]struct{})
		if isRelation && confDto.SonIds != "" {
			sonIdStrs := strings.Split(confDto.SonIds, ",")
			sonIds = make([]int64, 0, len(sonIdStrs))
			for _, s := range sonIdStrs {
				id, _ := strconv.ParseInt(strings.TrimSpace(s), 10, 64)
				sonIds = append(sonIds, id)
				sonIdSet[id] = struct{}{}
			}
		}

		// Handle relation node setup
		if isRelation {
			tmpNode := tmpConfMap[confDto.Id]
			if tmpNode != nil {
				if len(sonIds) > 0 {
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

				// Clean up old parent-child relationships for children no longer in sonIds
				// (Java lines 108-125)
				if originRel, ok := origin.(node.RelationNode); ok {
					originChildren := originRel.GetChildren()
					if originChildren != nil && originChildren.Size() > 0 {
						for x := originChildren.First(); x != nil; x = x.Next {
							sonNode := x.Item
							if sonNode != nil {
								sonNodeId := sonNode.GetNodeId()
								if _, stillExists := sonIdSet[sonNodeId]; !stillExists {
									// This child is no longer in the new sonIds, remove parent reference
									if parentIds := parentIdsMap[sonNodeId]; parentIds != nil {
										delete(parentIds, confDto.Id)
									}
								}
							}
						}
					}
				}
			}
		} else {
			// Current node is NOT a relation node
			// If origin was a relation node, clean up all its old children's parent references
			// (Java lines 126-145)
			if originRel, ok := origin.(node.RelationNode); ok {
				originChildren := originRel.GetChildren()
				if originChildren != nil && originChildren.Size() > 0 {
					for x := originChildren.First(); x != nil; x = x.Next {
						sonNode := x.Item
						if sonNode != nil {
							if parentIds := parentIdsMap[sonNode.GetNodeId()]; parentIds != nil {
								delete(parentIds, confDto.Id)
							}
						}
					}
				}
			}
		}

		// Handle forward node cleanup and setup (Java lines 146-176)
		// First, clean up old forward reference if it changed
		if origin != nil {
			if ba, ok := origin.(node.BaseAccessor); ok {
				oldForward := ba.GetForward()
				if oldForward != nil {
					oldForwardId := oldForward.GetNodeId()
					// If new forwardId is different or not set, remove old reference
					if confDto.ForwardId == 0 || confDto.ForwardId != oldForwardId {
						if forwardUseIds := forwardUseIdsMap[oldForwardId]; forwardUseIds != nil {
							delete(forwardUseIds, confDto.Id)
						}
					}
				}
			}
		}

		// Set up new forward reference
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

	// Update parent nodes' children lists (Java lines 179-226)
	for _, confDto := range confDtos {
		// Update parents' children lists
		parentIds := parentIdsMap[confDto.Id]
		var removeParentIds []int64
		for parentId := range parentIds {
			parentNode := confMap[parentId]
			if parentNode == nil {
				errors = append(errors, "parentId not exist: "+strconv.FormatInt(parentId, 10))
				log.Error(context.Background(), "parentId not exist", "parentId", parentId)
				continue
			}
			if rn, ok := parentNode.(node.RelationNode); ok {
				sonIds := rn.GetSonIds()
				if len(sonIds) > 0 {
					children := linkedlist.New[node.Node]()
					for _, sonId := range sonIds {
						child := confMap[sonId]
						if child != nil {
							children.Add(child)
						}
					}
					setRelationChildren(parentNode, children)
				}
			} else {
				// Parent is no longer a relation node, mark for removal
				removeParentIds = append(removeParentIds, parentId)
			}
		}
		// Remove invalid parent references
		for _, pid := range removeParentIds {
			delete(parentIds, pid)
		}

		// Update forward references for nodes using this conf as forward
		forwardUseIds := forwardUseIdsMap[confDto.Id]
		for forwardUseId := range forwardUseIds {
			useNode := confMap[forwardUseId]
			if useNode == nil {
				errors = append(errors, "forwardUseId not exist: "+strconv.FormatInt(forwardUseId, 10))
				log.Error(context.Background(), "forwardUseId not exist", "forwardUseId", forwardUseId)
				continue
			}
			forwardNode := confMap[confDto.Id]
			if forwardNode != nil {
				setForward(useNode, forwardNode)
			}
		}

		// Update handler roots
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
		// Also clean up parentIdsMap and forwardUseIdsMap
		delete(parentIdsMap, id)
		delete(forwardUseIdsMap, id)
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

// ClearAll clears all caches. Used for testing.
func ClearAll() {
	confMu.Lock()
	defer confMu.Unlock()

	confMap = make(map[int64]node.Node)
	parentIdsMap = make(map[int64]map[int64]struct{})
	forwardUseIdsMap = make(map[int64]map[int64]struct{})
}

"""Configuration cache for Ice SDK.

This module mirrors Java's IceConfCache logic for consistency.
"""

from __future__ import annotations

import threading
from typing import TYPE_CHECKING

from ice.dto import ConfDto
from ice.enums import NodeType, RunState, TimeType
from ice.node.base import Base, Node
from ice.node.relation import Relation
from ice.relation.serial import And, Any, All, TrueNode, NoneNode
from ice.relation.parallel import ParallelAnd, ParallelAny, ParallelAll, ParallelTrue, ParallelNone
from ice.leaf.registry import create_leaf_node, set_leaf_base
from ice import log

if TYPE_CHECKING:
    pass


# Global caches with locks
_conf_map: dict[int, Node] = {}
_parent_ids_map: dict[int, set[int]] = {}
_forward_use_ids_map: dict[int, set[int]] = {}
_conf_lock = threading.RLock()


def get_conf(conf_id: int) -> Node | None:
    """Get a node by its configuration ID."""
    with _conf_lock:
        return _conf_map.get(conf_id)


def get_conf_map() -> dict[int, Node]:
    """Get a copy of the conf map."""
    with _conf_lock:
        return _conf_map.copy()


def insert_or_update_confs(confs: list[ConfDto]) -> list[str]:
    """Insert or update configurations.
    
    This method mirrors Java's IceConfCache.insertOrUpdate logic.
    Returns a list of error messages.
    """
    if not confs:
        return []
    
    errors: list[str] = []
    
    with _conf_lock:
        # Build temporary map for new nodes
        tmp_conf_map: dict[int, Node] = {}
        
        for conf_dto in confs:
            try:
                node = _convert_node(conf_dto)
                if node:
                    tmp_conf_map[conf_dto.id] = node
            except Exception as e:
                errors.append(f"failed to convert node: {e}, confId: {conf_dto.id}")
                log.error("failed to convert node", error=str(e), confId=conf_dto.id)
        
        # Set up relationships and handle cleanup of old relationships
        for conf_dto in confs:
            origin = _conf_map.get(conf_dto.id)
            is_relation = NodeType(conf_dto.type).is_relation() if conf_dto.type in NodeType._value2member_map_ else False
            
            node = tmp_conf_map.get(conf_dto.id)
            if node is None:
                continue
            
            # Parse new sonIds
            son_ids: list[int] = []
            son_id_set: set[int] = set()
            if is_relation and conf_dto.sonIds:
                son_ids = _parse_son_ids(conf_dto.sonIds)
                son_id_set = set(son_ids)
            
            # Handle relation node setup
            if is_relation and isinstance(node, Relation):
                if son_ids:
                    children: list[Node] = []
                    for son_id in son_ids:
                        # Track parent-child relationships
                        if son_id not in _parent_ids_map:
                            _parent_ids_map[son_id] = set()
                        _parent_ids_map[son_id].add(conf_dto.id)
                        
                        child = tmp_conf_map.get(son_id) or _conf_map.get(son_id)
                        if child is None:
                            errors.append(f"sonId not exist: {son_id}")
                            log.error("sonId not exist", sonId=son_id)
                        else:
                            children.append(child)
                    node.set_children(children)
                    node.set_son_ids(son_ids)
                
                # Clean up old parent-child relationships (Java lines 108-125)
                if origin is not None and isinstance(origin, Relation):
                    origin_children = origin.get_children()
                    if origin_children:
                        for son_node in origin_children:
                            if son_node is not None:
                                son_node_id = son_node.get_node_id()
                                if son_node_id not in son_id_set:
                                    # Child no longer in sonIds, remove parent reference
                                    parent_ids = _parent_ids_map.get(son_node_id)
                                    if parent_ids:
                                        parent_ids.discard(conf_dto.id)
            else:
                # Current node is NOT a relation node
                # If origin was a relation node, clean up all old children's parent references (Java lines 126-145)
                if origin is not None and isinstance(origin, Relation):
                    origin_children = origin.get_children()
                    if origin_children:
                        for son_node in origin_children:
                            if son_node is not None:
                                parent_ids = _parent_ids_map.get(son_node.get_node_id())
                                if parent_ids:
                                    parent_ids.discard(conf_dto.id)
            
            # Handle forward node cleanup and setup (Java lines 146-176)
            # First, clean up old forward reference if it changed
            if origin is not None:
                old_forward = _get_forward(origin)
                if old_forward is not None:
                    old_forward_id = old_forward.get_node_id()
                    # If new forwardId is different or not set, remove old reference
                    if conf_dto.forwardId == 0 or conf_dto.forwardId != old_forward_id:
                        forward_use_ids = _forward_use_ids_map.get(old_forward_id)
                        if forward_use_ids:
                            forward_use_ids.discard(conf_dto.id)
            
            # Set up new forward reference
            if conf_dto.forwardId > 0:
                if conf_dto.forwardId not in _forward_use_ids_map:
                    _forward_use_ids_map[conf_dto.forwardId] = set()
                _forward_use_ids_map[conf_dto.forwardId].add(conf_dto.id)
                
                forward = tmp_conf_map.get(conf_dto.forwardId) or _conf_map.get(conf_dto.forwardId)
                if forward is None:
                    errors.append(f"forwardId not exist: {conf_dto.forwardId}")
                    log.error("forwardId not exist", forwardId=conf_dto.forwardId)
                else:
                    _set_forward(node, forward)
        
        # Update global map
        _conf_map.update(tmp_conf_map)
        
        # Update parent nodes' children lists (Java lines 179-226)
        for conf_dto in confs:
            conf_id = conf_dto.id
            
            # Update parents' children lists
            parent_ids = _parent_ids_map.get(conf_id)
            if parent_ids:
                remove_parent_ids: list[int] = []
                for parent_id in list(parent_ids):
                    parent = _conf_map.get(parent_id)
                    if parent is None:
                        errors.append(f"parentId not exist: {parent_id}")
                        log.error("parentId not exist", parentId=parent_id)
                        continue
                    if isinstance(parent, Relation):
                        son_ids = parent.get_son_ids()
                        if son_ids:
                            new_children = []
                            for son_id in son_ids:
                                child = _conf_map.get(son_id)
                                if child:
                                    new_children.append(child)
                            parent.set_children(new_children)
                    else:
                        # Parent is no longer a relation node, mark for removal
                        remove_parent_ids.append(parent_id)
                # Remove invalid parent references
                for pid in remove_parent_ids:
                    parent_ids.discard(pid)
            
            # Update forward users
            forward_use_ids = _forward_use_ids_map.get(conf_id)
            if forward_use_ids:
                for forward_use_id in list(forward_use_ids):
                    user = _conf_map.get(forward_use_id)
                    if user is None:
                        errors.append(f"forwardUseId not exist: {forward_use_id}")
                        log.error("forwardUseId not exist", forwardUseId=forward_use_id)
                        continue
                    forward_node = _conf_map.get(conf_id)
                    if forward_node:
                        _set_forward(user, forward_node)
            
            # Update handler roots
            tmp_node = _conf_map.get(conf_id)
            if tmp_node:
                from ice.cache.handler_cache import update_handler_root
                update_handler_root(tmp_node)
    
    return errors


def delete_confs(conf_ids: list[int]) -> None:
    """Delete configurations by IDs."""
    if not conf_ids:
        return
    
    with _conf_lock:
        for conf_id in conf_ids:
            if conf_id in _conf_map:
                del _conf_map[conf_id]
            if conf_id in _parent_ids_map:
                del _parent_ids_map[conf_id]
            if conf_id in _forward_use_ids_map:
                del _forward_use_ids_map[conf_id]


def clear_all() -> None:
    """Clear all caches."""
    with _conf_lock:
        _conf_map.clear()
        _parent_ids_map.clear()
        _forward_use_ids_map.clear()


def _convert_node(conf_dto: ConfDto) -> Node | None:
    """Convert a ConfDto to a Node."""
    node_type = NodeType(conf_dto.type) if conf_dto.type in NodeType._value2member_map_ else None
    
    if node_type is None:
        return None
    
    node: Node | None = None
    
    # Create node based on type
    if node_type == NodeType.NONE:
        node = NoneNode()
    elif node_type == NodeType.AND:
        node = And()
    elif node_type == NodeType.TRUE:
        node = TrueNode()
    elif node_type == NodeType.ALL:
        node = All()
    elif node_type == NodeType.ANY:
        node = Any()
    elif node_type == NodeType.P_NONE:
        node = ParallelNone()
    elif node_type == NodeType.P_AND:
        node = ParallelAnd()
    elif node_type == NodeType.P_TRUE:
        node = ParallelTrue()
    elif node_type == NodeType.P_ALL:
        node = ParallelAll()
    elif node_type == NodeType.P_ANY:
        node = ParallelAny()
    elif node_type in (NodeType.LEAF_FLOW, NodeType.LEAF_RESULT, NodeType.LEAF_NONE):
        if conf_dto.confName:
            node = create_leaf_node(conf_dto.confName, conf_dto.confField)
    else:
        # Try to create as leaf
        if conf_dto.confName:
            node = create_leaf_node(conf_dto.confName, conf_dto.confField)
    
    # Set common properties
    if node is not None:
        _set_node_properties(node, conf_dto)
    
    return node


def _set_node_properties(node: Node, conf_dto: ConfDto) -> None:
    """Set common node properties from ConfDto."""
    base = Base()
    base.ice_node_id = conf_dto.id
    base.ice_node_debug = conf_dto.debug in (0, 1)
    base.ice_inverse = conf_dto.inverse
    base.ice_time_type = TimeType(conf_dto.timeType) if conf_dto.timeType in TimeType._value2member_map_ else TimeType.NONE
    base.ice_start = conf_dto.start
    base.ice_end = conf_dto.end
    if conf_dto.errorState != 0 and conf_dto.errorState in RunState._value2member_map_:
        base.ice_error_state = RunState(conf_dto.errorState)
    base.ice_type = NodeType(conf_dto.type) if conf_dto.type in NodeType._value2member_map_ else NodeType.LEAF_NONE
    
    # Set log name
    if isinstance(node, Relation):
        # Already set in constructor
        pass
    else:
        base.ice_log_name = _get_simple_name(conf_dto.confName)
    
    # Apply base properties
    if isinstance(node, Relation):
        node.ice_node_id = base.ice_node_id
        node.ice_node_debug = base.ice_node_debug
        node.ice_inverse = base.ice_inverse
        node.ice_time_type = base.ice_time_type
        node.ice_start = base.ice_start
        node.ice_end = base.ice_end
        node.ice_error_state = base.ice_error_state
        node.ice_type = base.ice_type
    else:
        set_leaf_base(node, base)


def _set_forward(node: Node, forward: Node | None) -> None:
    """Set the forward node on any node type."""
    if hasattr(node, "set_forward"):
        node.set_forward(forward)
    elif hasattr(node, "ice_forward"):
        node.ice_forward = forward


def _get_forward(node: Node) -> Node | None:
    """Get the forward node from any node type."""
    if hasattr(node, "get_forward"):
        return node.get_forward()
    elif hasattr(node, "ice_forward"):
        return node.ice_forward
    return None


def _parse_son_ids(son_ids_str: str) -> list[int]:
    """Parse comma-separated son IDs string."""
    if not son_ids_str:
        return []
    result = []
    for s in son_ids_str.split(","):
        s = s.strip()
        if s:
            try:
                result.append(int(s))
            except ValueError:
                pass
    return result


def _get_simple_name(full_name: str) -> str:
    """Extract the simple class name from a fully qualified name."""
    if not full_name:
        return ""
    idx = full_name.rfind(".")
    if idx >= 0:
        return full_name[idx + 1:]
    return full_name

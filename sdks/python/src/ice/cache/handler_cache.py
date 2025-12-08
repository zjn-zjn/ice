"""Handler cache for Ice SDK.

This module mirrors Java's IceHandlerCache logic for consistency.
"""

from __future__ import annotations

import threading
from typing import TYPE_CHECKING

from ice.dto import BaseDto
from ice.enums import TimeType
from ice.handler.handler import Handler
from ice.cache.conf_cache import get_conf
from ice.node.base import Node
from ice import log

if TYPE_CHECKING:
    pass


# Global caches with locks
# key: iceId, value: Handler
_ice_id_handler_map: dict[int, Handler] = {}
# key: scene, value: dict[iceId, Handler]  (like Java's LinkedHashMap for ordering)
_scene_handler_map: dict[str, dict[int, Handler]] = {}
# key: confId, value: dict[iceId, Handler]  (one confId can have multiple handlers)
_conf_id_handler_map: dict[int, dict[int, Handler]] = {}
_handler_lock = threading.RLock()


def get_handler_by_id(ice_id: int) -> Handler | None:
    """Get a handler by its ice ID."""
    with _handler_lock:
        return _ice_id_handler_map.get(ice_id)


def get_handlers_by_scene(scene: str) -> dict[int, Handler] | None:
    """Get all handlers for a scene as a copy of the map."""
    with _handler_lock:
        original = _scene_handler_map.get(scene)
        if original is None:
            return None
        return original.copy()


def get_handler_by_conf_id(conf_id: int) -> dict[int, Handler] | None:
    """Get handlers by conf ID as a copy of the map."""
    with _handler_lock:
        original = _conf_id_handler_map.get(conf_id)
        if original is None:
            return None
        return original.copy()


def get_id_handler_map() -> dict[int, Handler]:
    """Get a copy of the id handler map."""
    with _handler_lock:
        return _ice_id_handler_map.copy()


def insert_or_update_handlers(bases: list[BaseDto]) -> list[str]:
    """Insert or update handlers from base DTOs.
    
    This method mirrors Java's IceHandlerCache.insertOrUpdate logic.
    Returns a list of error messages.
    """
    if not bases:
        return []
    
    errors: list[str] = []
    
    with _handler_lock:
        for base_dto in bases:
            handler = Handler()
            handler.ice_id = base_dto.id
            handler.time_type = TimeType(base_dto.timeType) if base_dto.timeType in TimeType._value2member_map_ else TimeType.NONE
            handler.start = base_dto.start
            handler.end = base_dto.end
            handler.debug = base_dto.debug
            handler.priority = base_dto.priority
            
            conf_id = base_dto.confId
            if conf_id != 0:
                root = get_conf(conf_id)
                if root is None:
                    errors.append(f"confId not exist: {conf_id}")
                    log.error("confId not exist", confId=conf_id)
                    continue
                
                # Track in confIdHandlersMap
                handler_map = _conf_id_handler_map.get(conf_id)
                if handler_map is None:
                    handler_map = {}
                    _conf_id_handler_map[conf_id] = handler_map
                handler_map[handler.ice_id] = handler
                
                handler.root = root
                handler.conf_id = conf_id
            
            # Parse scenes
            if base_dto.scenes:
                scenes = set()
                for scene in base_dto.scenes.split(","):
                    scene = scene.strip()
                    if scene:
                        scenes.add(scene)
                handler.scenes = scenes
            else:
                handler.scenes = set()
            
            _online_or_update_handler(handler)
    
    return errors


def delete_handlers(ids: list[int]) -> None:
    """Delete handlers by IDs."""
    if not ids:
        return
    
    with _handler_lock:
        for ice_id in ids:
            handler = _ice_id_handler_map.get(ice_id)
            if handler is not None:
                # Remove from confIdHandlersMap (Java lines 97-99)
                if handler.conf_id != 0:
                    handler_map = _conf_id_handler_map.get(handler.conf_id)
                    if handler_map is not None:
                        handler_map.pop(handler.ice_id, None)
                        if len(handler_map) == 0:
                            del _conf_id_handler_map[handler.conf_id]
                _offline_handler(handler)


def update_handler_root(conf_node: Node) -> None:
    """Update the root node for handlers with the given conf node.
    
    This method mirrors Java's IceHandlerCache.updateHandlerRoot.
    """
    if conf_node is None:
        return
    conf_id = conf_node.get_node_id()
    
    with _handler_lock:
        handler_map = _conf_id_handler_map.get(conf_id)
        if handler_map is not None:
            for handler in handler_map.values():
                handler.root = conf_node


def _online_or_update_handler(handler: Handler) -> None:
    """Add or update a handler. Must be called with _handler_lock held."""
    origin_handler: Handler | None = None
    if handler.ice_id > 0:
        origin_handler = _ice_id_handler_map.get(handler.ice_id)
        _ice_id_handler_map[handler.ice_id] = handler
    
    # Remove from scenes that are no longer in the new handler (Java lines 110-136)
    if origin_handler is not None and len(origin_handler.scenes) > 0:
        if len(handler.scenes) == 0:
            # New handler has no scenes, remove from all old scenes
            for scene in origin_handler.scenes:
                handler_map = _scene_handler_map.get(scene)
                if handler_map is not None:
                    handler_map.pop(origin_handler.ice_id, None)
                    if len(handler_map) == 0:
                        del _scene_handler_map[scene]
            return
        
        # Remove from scenes that exist in old but not in new
        for scene in origin_handler.scenes:
            if scene not in handler.scenes:
                handler_map = _scene_handler_map.get(scene)
                if handler_map is not None:
                    handler_map.pop(origin_handler.ice_id, None)
                    if len(handler_map) == 0:
                        del _scene_handler_map[scene]
    
    # Add to new scenes (Java lines 137-144)
    for scene in handler.scenes:
        handler_map = _scene_handler_map.get(scene)
        if handler_map is None:
            handler_map = {}
            _scene_handler_map[scene] = handler_map
        handler_map[handler.ice_id] = handler


def _offline_handler(handler: Handler) -> None:
    """Remove a handler from all maps. Must be called with _handler_lock held."""
    if handler is None:
        return
    _ice_id_handler_map.pop(handler.ice_id, None)
    for scene in handler.scenes:
        handler_map = _scene_handler_map.get(scene)
        if handler_map is not None:
            handler_map.pop(handler.ice_id, None)
            if len(handler_map) == 0:
                del _scene_handler_map[scene]


def clear_all() -> None:
    """Clear all handler caches."""
    with _handler_lock:
        _ice_id_handler_map.clear()
        _scene_handler_map.clear()
        _conf_id_handler_map.clear()

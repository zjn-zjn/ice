"""Dispatcher for Ice SDK - routes roam to handlers."""

from __future__ import annotations

from typing import TYPE_CHECKING

from ice.context.roam import Roam
from ice.cache.handler_cache import (
    get_handler_by_id as _get_handler_by_id,
    get_handlers_by_scene as _get_handlers_by_scene,
)
from ice.cache.conf_cache import get_conf as _get_conf
from ice.handler.handler import Handler
from ice import log

if TYPE_CHECKING:
    from ice.handler.handler import Handler


def sync_process(roam: Roam) -> list[Roam]:
    """
    Synchronously process a roam.

    Dispatches to handler based on IceMeta:
    1. meta.id - Direct handler lookup
    2. meta.scene - Scene-based handler lookup
    3. meta.nid - Direct conf node execution

    Args:
        roam: The input roam to process

    Returns:
        List of roam objects after processing
    """
    if not _check_roam(roam):
        return []

    meta = roam.get_meta()
    results: list[Roam] = []

    # Priority 1: ice_id
    if meta.id > 0:
        handler = _get_handler_by_id(meta.id)
        if handler:
            handler.handle(roam)
            results.append(roam)
        return results

    # Priority 2: scene
    if meta.scene:
        handlers_map = _get_handlers_by_scene(meta.scene)
        if handlers_map:
            for handler in handlers_map.values():
                cloned = roam.clone()
                cloned.get_meta().id = handler.ice_id
                handler.handle(cloned)
                results.append(cloned)
        return results

    # Priority 3: nid (direct conf node execution)
    if meta.nid > 0:
        root = _get_conf(meta.nid)
        if root is not None:
            handler = Handler()
            handler.debug = meta.debug
            handler.root = root
            handler.handle_with_node_id(roam)
            results.append(roam)
        return results

    return results


async def async_process(roam: Roam) -> list[Roam]:
    """
    Asynchronously process a roam.

    Currently uses asyncio.to_thread to wrap the sync processing.

    Args:
        roam: The input roam to process

    Returns:
        List of roam objects after processing
    """
    import asyncio
    return await asyncio.to_thread(sync_process, roam)


def _check_roam(roam: Roam) -> bool:
    """Validate the roam before processing."""
    if roam is None:
        log.error("roam is None")
        return False

    meta = roam.get_meta()
    if meta is None:
        log.error("roam has no IceMeta (use Roam.create() to create)")
        return False

    if meta.id <= 0 and not meta.scene and meta.nid <= 0:
        log.error("roam meta must have id, scene, or nid", roam=roam.to_dict())
        return False

    return True


# ============ Convenience functions (matching Java/Go API) ============

def process_roam(roam: Roam) -> list[Roam]:
    """
    Synchronously process and return roam results.

    Args:
        roam: The input roam to process

    Returns:
        List of roam objects after processing
    """
    return sync_process(roam)


def process_single_roam(roam: Roam) -> Roam | None:
    """
    Synchronously process and return a single roam.

    Args:
        roam: The input roam to process

    Returns:
        The first roam, or None if no results
    """
    results = sync_process(roam)
    if not results:
        return None
    return results[0]


# ============ Handler access functions (matching Go API) ============

def get_handler_by_id(ice_id: int) -> Handler | None:
    """
    Get a handler by its ice ID.

    Args:
        ice_id: The handler's ice ID

    Returns:
        The handler, or None if not found
    """
    return _get_handler_by_id(ice_id)


def get_handlers_by_scene(scene: str) -> list[Handler]:
    """
    Get all handlers for a scene.

    Args:
        scene: The scene name

    Returns:
        List of handlers for the scene
    """
    handlers_map = _get_handlers_by_scene(scene)
    if handlers_map is None:
        return []
    return list(handlers_map.values())

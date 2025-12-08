"""Dispatcher for Ice SDK - routes packs to handlers."""

from __future__ import annotations

from typing import TYPE_CHECKING

from ice.context.context import Context
from ice.context.roam import Roam
from ice.cache.handler_cache import (
    get_handler_by_id as _get_handler_by_id,
    get_handlers_by_scene as _get_handlers_by_scene,
    get_handler_by_conf_id,
)
from ice import log

if TYPE_CHECKING:
    from ice.context.pack import Pack
    from ice.handler.handler import Handler


def sync_process(pack: Pack) -> list[Context]:
    """
    Synchronously process a pack.
    
    Dispatches to handler based on:
    1. pack.ice_id - Direct handler lookup
    2. pack.scene - Scene-based handler lookup
    3. pack.conf_id - Direct conf node execution
    
    Args:
        pack: The input pack to process
    
    Returns:
        List of contexts after processing
    """
    if not _check_pack(pack):
        return []
    
    results: list[Context] = []
    
    # Priority 1: ice_id
    if pack.ice_id > 0:
        handler = _get_handler_by_id(pack.ice_id)
        if handler:
            ctx = Context(pack.ice_id, pack)
            handler.handle(ctx)
            results.append(ctx)
        return results
    
    # Priority 2: scene
    if pack.scene:
        handlers_map = _get_handlers_by_scene(pack.scene)
        if handlers_map:
            for handler in handlers_map.values():
                ctx = Context(handler.ice_id, pack.clone())
                handler.handle(ctx)
                results.append(ctx)
        return results
    
    # Priority 3: conf_id
    if pack.conf_id > 0:
        handler = get_handler_by_conf_id(pack.conf_id)
        if handler:
            ctx = Context(0, pack)
            handler.handle_with_conf_id(ctx)
            results.append(ctx)
        return results
    
    return results


async def async_process(pack: Pack) -> list[Context]:
    """
    Asynchronously process a pack.
    
    Currently uses asyncio.to_thread to wrap the sync processing.
    
    Args:
        pack: The input pack to process
    
    Returns:
        List of contexts after processing
    """
    import asyncio
    return await asyncio.to_thread(sync_process, pack)


def _check_pack(pack: Pack) -> bool:
    """Validate the pack before processing."""
    if pack is None:
        log.error("pack is None")
        return False
    
    if pack.ice_id <= 0 and not pack.scene and pack.conf_id <= 0:
        log.error("pack must have ice_id, scene, or conf_id", pack=pack.to_dict())
        return False
    
    return True


# ============ Convenience functions (matching Java/Go API) ============

def process_ctx(pack: Pack) -> list[Context]:
    """
    Synchronously process and return contexts.
    
    This is an alias for sync_process, matching Java's processCtx.
    
    Args:
        pack: The input pack to process
    
    Returns:
        List of contexts after processing
    """
    return sync_process(pack)


def process_single_ctx(pack: Pack) -> Context | None:
    """
    Synchronously process and return a single context.
    
    Matches Java's processSingleCtx.
    
    Args:
        pack: The input pack to process
    
    Returns:
        The first context, or None if no results
    """
    ctx_list = sync_process(pack)
    if not ctx_list:
        return None
    return ctx_list[0]


def process_roam(pack: Pack) -> list[Roam]:
    """
    Synchronously process and return roam results.
    
    Matches Java's processRoam.
    
    Args:
        pack: The input pack to process
    
    Returns:
        List of roam objects after processing
    """
    ctx_list = sync_process(pack)
    if not ctx_list:
        return []
    return [ctx.pack.roam for ctx in ctx_list if ctx.pack is not None]


def process_single_roam(pack: Pack) -> Roam | None:
    """
    Synchronously process and return a single roam.
    
    Matches Java's processSingleRoam.
    
    Args:
        pack: The input pack to process
    
    Returns:
        The first roam, or None if no results
    """
    ctx = process_single_ctx(pack)
    if ctx is not None and ctx.pack is not None:
        return ctx.pack.roam
    return None


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
    Get all handlers for a scene, sorted by priority.
    
    Args:
        scene: The scene name
    
    Returns:
        List of handlers for the scene
    """
    handlers_map = _get_handlers_by_scene(scene)
    if handlers_map is None:
        return []
    # Sort by priority (descending) then by ice_id (ascending)
    return sorted(handlers_map.values(), key=lambda h: (-h.priority, h.ice_id))


"""Handler implementation for Ice SDK."""

from __future__ import annotations

from enum import IntFlag
from typing import TYPE_CHECKING

from ice.enums import TimeType
from ice._internal.timeutil import time_disabled
from ice import log

if TYPE_CHECKING:
    from ice.context.context import Context
    from ice.node.base import Node


class DebugFlag(IntFlag):
    """Debug flags for handler."""
    IN_PACK = 1
    PROCESS = 2
    OUT_ROAM = 4
    OUT_PACK = 8


class Handler:
    """
    Handler manages the execution of an ice rule tree.
    
    Corresponds to Java's IceHandler.
    """
    
    def __init__(self) -> None:
        self.ice_id: int = 0
        self.conf_id: int = 0
        self.scenes: set[str] = set()
        self.time_type: TimeType = TimeType.NONE
        self.start: int = 0
        self.end: int = 0
        self.debug: int = 0
        self.root: Node | None = None
        self.priority: int = 0
    
    def has_debug(self, flag: DebugFlag) -> bool:
        """Check if a debug flag is set."""
        return (self.debug & flag) != 0
    
    def handle(self, ctx: Context) -> None:
        """
        Execute the handler on the given context.
        
        Args:
            ctx: The execution context
        """
        if self.root is None:
            return
        
        # Check time constraints
        if time_disabled(self.time_type, ctx.pack.request_time, self.start, self.end):
            return
        
        # Debug: log input
        if self.has_debug(DebugFlag.IN_PACK):
            log.info("handle in", iceId=self.ice_id, pack=ctx.pack.to_dict())
        
        try:
            # Execute the root node
            self.root.process(ctx)
            
            # Debug: log process info
            if self.has_debug(DebugFlag.PROCESS):
                log.info("handle process", iceId=self.ice_id, process=ctx.get_process_info())
            
            # Debug: log output
            if self.has_debug(DebugFlag.OUT_PACK):
                log.info("handle out", iceId=self.ice_id, pack=ctx.pack.to_dict())
            elif self.has_debug(DebugFlag.OUT_ROAM):
                log.info("handle out", iceId=self.ice_id, roam=ctx.pack.roam.to_dict())
                
        except Exception as e:
            log.error("error in handler", iceId=self.ice_id, error=str(e))
            raise
    
    def handle_with_conf_id(self, ctx: Context) -> None:
        """
        Execute the handler using conf_id (for direct node execution).
        
        Args:
            ctx: The execution context
        """
        if self.root is None:
            return
        
        # Debug: log input
        if self.has_debug(DebugFlag.IN_PACK):
            log.info("handle confId in", confId=self.conf_id, pack=ctx.pack.to_dict())
        
        try:
            # Execute the root node
            self.root.process(ctx)
            
            # Debug: log process info
            if self.has_debug(DebugFlag.PROCESS):
                log.info("handle confId process", confId=self.conf_id, process=ctx.get_process_info())
            
            # Debug: log output
            if self.has_debug(DebugFlag.OUT_PACK):
                log.info("handle confId out", confId=self.conf_id, pack=ctx.pack.to_dict())
            elif self.has_debug(DebugFlag.OUT_ROAM):
                log.info("handle confId out", confId=self.conf_id, roam=ctx.pack.roam.to_dict())
                
        except Exception as e:
            log.error("error in handler confId", confId=self.conf_id, error=str(e))
            raise
    
    def __repr__(self) -> str:
        return f"Handler(ice_id={self.ice_id}, scenes={self.scenes})"


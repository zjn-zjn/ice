"""Pack implementation for Ice SDK."""

from __future__ import annotations

import json
import time
from typing import TYPE_CHECKING

from ice.context.roam import Roam
from ice._internal.uuid import generate_uuid22

if TYPE_CHECKING:
    pass


class Pack:
    """
    Pack is the input for ice processing.
    
    Contains:
    - ice_id: Handler ID to execute
    - scene: Scene name for matching handlers
    - conf_id: Direct conf node ID to execute
    - roam: Business data
    - debug: Debug flag
    - request_time: Request timestamp
    - trace_id: Trace ID for logging
    - priority: Execution priority
    """
    
    def __init__(
        self,
        ice_id: int = 0,
        scene: str = "",
        conf_id: int = 0,
        roam: Roam | None = None,
        debug: int = 0,
        request_time: int = 0,
        trace_id: str = "",
        priority: int = 0,
    ) -> None:
        self.ice_id = ice_id
        self.scene = scene
        self.conf_id = conf_id
        self.roam = roam if roam is not None else Roam()
        self.debug = debug
        self.request_time = request_time if request_time > 0 else int(time.time() * 1000)
        self.trace_id = trace_id if trace_id else generate_uuid22()
        self.priority = priority
    
    def set_ice_id(self, ice_id: int) -> Pack:
        """Set ice_id and return self for chaining."""
        self.ice_id = ice_id
        return self
    
    def set_scene(self, scene: str) -> Pack:
        """Set scene and return self for chaining."""
        self.scene = scene
        return self
    
    def set_conf_id(self, conf_id: int) -> Pack:
        """Set conf_id and return self for chaining."""
        self.conf_id = conf_id
        return self
    
    def set_roam(self, roam: Roam) -> Pack:
        """Set roam and return self for chaining."""
        self.roam = roam
        return self
    
    def set_debug(self, debug: int) -> Pack:
        """Set debug and return self for chaining."""
        self.debug = debug
        return self
    
    def set_trace_id(self, trace_id: str) -> Pack:
        """Set trace_id and return self for chaining."""
        self.trace_id = trace_id
        return self
    
    def set_priority(self, priority: int) -> Pack:
        """Set priority and return self for chaining."""
        self.priority = priority
        return self
    
    def clone(self) -> Pack:
        """Create a copy of the pack with a shallow copy of roam."""
        return Pack(
            ice_id=self.ice_id,
            scene=self.scene,
            conf_id=self.conf_id,
            roam=self.roam.shallow_copy(),
            debug=self.debug,
            request_time=self.request_time,
            trace_id=self.trace_id,
            priority=self.priority,
        )
    
    def to_dict(self) -> dict:
        """Convert to dictionary."""
        return {
            "iceId": self.ice_id,
            "scene": self.scene,
            "confId": self.conf_id,
            "roam": self.roam.to_dict(),
            "debug": self.debug,
            "requestTime": self.request_time,
            "traceId": self.trace_id,
            "priority": self.priority,
        }
    
    def __str__(self) -> str:
        """Return JSON representation."""
        try:
            return json.dumps(self.to_dict(), ensure_ascii=False, default=str)
        except Exception as e:
            return f"{{\"error\": \"{e}\"}}"
    
    def __repr__(self) -> str:
        return f"Pack(ice_id={self.ice_id}, scene={self.scene}, conf_id={self.conf_id})"


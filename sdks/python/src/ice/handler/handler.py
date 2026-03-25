"""Handler implementation for Ice SDK."""

from __future__ import annotations

from enum import IntFlag
from typing import TYPE_CHECKING

from ice.enums import TimeType
from ice._internal.timeutil import time_disabled
from ice import log

if TYPE_CHECKING:
    from ice.context.roam import Roam
    from ice.node.base import Node


class DebugFlag(IntFlag):
    """Debug flags for handler."""
    IN_ROAM = 1
    PROCESS = 2
    OUT_ROAM = 4


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

    def has_debug(self, flag: DebugFlag) -> bool:
        """Check if a debug flag is set."""
        return (self.debug & flag) != 0

    def _meta_suffix(self, roam: Roam) -> dict:
        """Build log suffix from meta: id/scene/nid/ts, only include non-zero values."""
        s: dict = {}
        if roam.get_id() > 0:
            s["id"] = roam.get_id()
        scene = roam.get_scene()
        if scene:
            s["scene"] = scene
        if roam.get_nid() > 0:
            s["nid"] = roam.get_nid()
        s["ts"] = roam.get_ts()
        return s

    def handle(self, roam: Roam) -> None:
        if self.root is None:
            return

        if time_disabled(self.time_type, roam.get_ts(), self.start, self.end):
            return

        from ice.log import set_trace_id, reset_trace_id
        token = set_trace_id(roam.get_trace())
        try:
            if self.has_debug(DebugFlag.IN_ROAM):
                log.info("handle input", extra={"roam": roam, **self._meta_suffix(roam)})

            self.root.process(roam)

            if self.has_debug(DebugFlag.PROCESS):
                log.info("handle process", extra={"process": roam.get_process_info(), **self._meta_suffix(roam)})

            if self.has_debug(DebugFlag.OUT_ROAM):
                log.info("handle output", extra={"roam": roam, **self._meta_suffix(roam)})

        except Exception as e:
            log.error("handler error", extra={"error": str(e), **self._meta_suffix(roam)})
            raise
        finally:
            reset_trace_id(token)

    def handle_with_node_id(self, roam: Roam) -> None:
        if self.root is None:
            return

        from ice.log import set_trace_id, reset_trace_id
        token = set_trace_id(roam.get_trace())
        try:
            if self.has_debug(DebugFlag.IN_ROAM):
                log.info("handle input", extra={"roam": roam, **self._meta_suffix(roam)})

            self.root.process(roam)

            if self.has_debug(DebugFlag.PROCESS):
                log.info("handle process", extra={"process": roam.get_process_info(), **self._meta_suffix(roam)})

            if self.has_debug(DebugFlag.OUT_ROAM):
                log.info("handle output", extra={"roam": roam, **self._meta_suffix(roam)})

        except Exception as e:
            log.error("handler error", extra={"error": str(e), **self._meta_suffix(roam)})
            raise
        finally:
            reset_trace_id(token)

    def __repr__(self) -> str:
        return f"Handler(ice_id={self.ice_id}, scenes={self.scenes})"

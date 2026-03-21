"""IceMeta implementation for Ice SDK."""

from __future__ import annotations

import io
import time

from ice._internal.uuid import generate_alphanum_id


class IceMeta:
    """
    IceMeta holds ice-internal metadata for processing.

    Stored under the "_ice" key in Roam, invisible to user code.
    Contains: id, scene, nid, ts, trace, debug, process.
    """

    def __init__(
        self,
        id: int = 0,
        scene: str = "",
        nid: int = 0,
        ts: int | None = None,
        trace: str | None = None,
        debug: int = 0,
        process: io.StringIO | None = None,
    ) -> None:
        self.id = id
        self.scene = scene
        self.nid = nid
        self.ts = ts if ts is not None else int(time.time() * 1000)
        self.trace = trace if trace else generate_alphanum_id(11)
        self.debug = debug
        self.process = process if process is not None else io.StringIO()

    def clone(self) -> IceMeta:
        """Copy all fields but create a fresh process StringIO."""
        return IceMeta(
            id=self.id,
            scene=self.scene,
            nid=self.nid,
            ts=self.ts,
            trace=self.trace,
            debug=self.debug,
        )

    def get_process_info(self) -> str:
        """Get collected process information."""
        return self.process.getvalue()

    def to_dict(self) -> dict:
        """Convert to dictionary for JSON serialization."""
        d: dict = {}
        if self.id:
            d["id"] = self.id
        if self.scene:
            d["scene"] = self.scene
        if self.nid:
            d["nid"] = self.nid
        if self.ts:
            d["ts"] = self.ts
        if self.debug:
            d["debug"] = self.debug
        process = self.process.getvalue()
        if process:
            d["process"] = process
        return d

    def __repr__(self) -> str:
        return f"IceMeta(id={self.id}, scene={self.scene}, trace={self.trace})"

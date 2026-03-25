"""Ice execution metadata."""

from __future__ import annotations

import io
import time

from ice._internal.uuid import generate_alphanum_id


class Meta:
    """Ice execution metadata."""

    __slots__ = ("id", "scene", "nid", "ts", "trace", "debug", "process")

    def __init__(
        self,
        *,
        id: int = 0,
        scene: str = "",
        nid: int = 0,
        ts: int = 0,
        trace: str = "",
        debug: int = 0,
    ) -> None:
        self.id = id
        self.scene = scene
        self.nid = nid
        self.ts = ts or int(time.time() * 1000)
        self.trace = trace or generate_alphanum_id(11)
        self.debug = debug
        self.process: io.StringIO = io.StringIO()

    def clone(self) -> Meta:
        """Clone with fresh process buffer."""
        return Meta(
            id=self.id,
            scene=self.scene,
            nid=self.nid,
            ts=self.ts,
            trace=self.trace,
            debug=self.debug,
        )

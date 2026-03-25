"""Thread-safe Roam implementation for Ice SDK."""

from __future__ import annotations

import io
import json
import threading
from typing import Any

from ice.context.meta import Meta


class Roam:
    """
    Thread-safe dictionary for business data in ice processing.

    Supports:
    - Basic put/get/del operations
    - Deep key access (a.b.c)
    - Resolve reference (@key)
    - Structured metadata via Meta
    """

    def __init__(self) -> None:
        self._data: dict[str, Any] = {}
        self._lock = threading.RLock()
        self._meta: Meta | None = None

    # ============ meta getters ============

    def get_meta(self) -> Meta | None:
        """Get the metadata."""
        return self._meta

    def get_id(self) -> int:
        """Get handler ID."""
        return self._meta.id if self._meta else 0

    def get_scene(self) -> str:
        """Get scene."""
        return self._meta.scene if self._meta else ""

    def get_ts(self) -> int:
        """Get request timestamp."""
        return self._meta.ts if self._meta else 0

    def get_trace(self) -> str:
        """Get trace ID."""
        return self._meta.trace if self._meta else ""

    def get_process(self) -> io.StringIO:
        """Get process StringIO for debug info."""
        if self._meta and self._meta.process:
            return self._meta.process
        return io.StringIO()

    def get_debug(self) -> int:
        """Get debug flag."""
        return self._meta.debug if self._meta else 0

    def get_nid(self) -> int:
        """Get node ID."""
        return self._meta.nid if self._meta else 0

    def get_process_info(self) -> str:
        """Get collected process information string."""
        return self.get_process().getvalue()

    # ============ meta setters ============

    def set_id(self, id: int) -> None:
        """Set handler ID."""
        if self._meta:
            self._meta.id = id

    def set_scene(self, scene: str) -> None:
        """Set scene."""
        if self._meta:
            self._meta.scene = scene

    def set_ts(self, ts: int) -> None:
        """Set request timestamp."""
        if self._meta:
            self._meta.ts = ts

    def set_trace(self, trace: str) -> None:
        """Set trace ID."""
        if self._meta:
            self._meta.trace = trace

    def set_debug(self, debug: int) -> None:
        """Set debug flag."""
        if self._meta:
            self._meta.debug = debug

    def set_nid(self, nid: int) -> None:
        """Set node ID."""
        if self._meta:
            self._meta.nid = nid

    # ============ Factory / Clone ============

    @classmethod
    def create(cls, **kwargs) -> Roam:
        """Create a Roam with default metadata.

        Keyword args: id, scene, nid, ts, trace, debug.
        """
        roam = cls()
        roam._meta = Meta(
            id=kwargs.get("id", 0),
            scene=kwargs.get("scene", ""),
            nid=kwargs.get("nid", 0),
            ts=kwargs.get("ts", 0),
            trace=kwargs.get("trace", ""),
            debug=kwargs.get("debug", 0),
        )
        return roam

    def clone(self) -> Roam:
        """Shallow-copy data and clone meta with a fresh process."""
        new_roam = Roam()
        with self._lock:
            for k, v in self._data.items():
                new_roam._data[k] = v
        if self._meta:
            new_roam._meta = self._meta.clone()
        return new_roam

    # ============ Put operations ============

    def put(self, key: str, value: Any) -> Roam:
        """Put a value into roam."""
        with self._lock:
            self._data[key] = value
        return self

    def put_all(self, data: dict[str, Any]) -> Roam:
        """Put multiple key-value pairs at once."""
        with self._lock:
            for k, v in data.items():
                self._data[k] = v
        return self

    def put_deep(self, multi_key: str, value: Any) -> Any:
        """Put value using deep key (e.g., 'a.b.c')."""
        if multi_key is None:
            return None

        keys = multi_key.split(".")
        if len(keys) == 1:
            with self._lock:
                old = self._data.get(keys[0])
                self._data[keys[0]] = value
                return old

        with self._lock:
            end = self._data
            for i in range(len(keys) - 1):
                k = keys[i]
                if isinstance(end, dict):
                    if k not in end:
                        end[k] = {}
                    nxt = end[k]
                    if isinstance(nxt, dict):
                        end = nxt
                    elif isinstance(nxt, list):
                        end = nxt
                    else:
                        end[k] = {}
                        end = end[k]
                elif isinstance(end, list):
                    try:
                        end = end[int(k)]
                    except (ValueError, IndexError):
                        return None
                else:
                    return None
            last = keys[-1]
            if isinstance(end, dict):
                old = end.get(last)
                end[last] = value
                return old
            elif isinstance(end, list):
                try:
                    idx = int(last)
                    old = end[idx]
                    end[idx] = value
                    return old
                except (ValueError, IndexError):
                    return None
            return None

    # ============ Get operations ============

    def get(self, key: str, default: Any = None) -> Any:
        """Get a value from roam."""
        with self._lock:
            return self._data.get(key, default)

    def get_deep(self, key: str, default: Any = None) -> Any:
        """Get value using deep key (e.g., 'a.b.c')."""
        with self._lock:
            keys = key.split(".")
            value = self._data
            for k in keys:
                if isinstance(value, dict):
                    value = value.get(k)
                    if value is None:
                        return default
                elif isinstance(value, list):
                    try:
                        value = value[int(k)]
                    except (ValueError, IndexError):
                        return default
                else:
                    return default
            return value

    def resolve(self, value: Any) -> Any:
        """Resolve reference. '@key' -> get(key), '@a.b' -> get_deep('a.b')."""
        if isinstance(value, str) and value.startswith("@"):
            ref_key = value[1:]
            if "." in ref_key:
                return self.get_deep(ref_key)
            return self.get(ref_key)
        return value

    # ============ Delete operations ============

    def delete(self, key: str) -> Any:
        """Delete a key and return its value."""
        with self._lock:
            return self._data.pop(key, None)

    def delete_deep(self, deep_key: str) -> Any:
        """Delete a value using a dot-separated key path (e.g., 'a.b.c')."""
        if deep_key is None:
            return None
        keys = deep_key.split(".")
        if len(keys) == 1:
            return self.delete(keys[0])

        with self._lock:
            end = self._data
            for i in range(len(keys) - 1):
                k = keys[i]
                if isinstance(end, dict):
                    end = end.get(k)
                    if end is None:
                        return None
                elif isinstance(end, list):
                    try:
                        end = end[int(k)]
                    except (ValueError, IndexError):
                        return None
                else:
                    return None
            last = keys[-1]
            if isinstance(end, dict):
                return end.pop(last, None)
            return None

    # ============ Utility operations ============

    def contains(self, key: str) -> bool:
        """Check if key exists."""
        with self._lock:
            return key in self._data

    def clear(self) -> None:
        """Clear all data (preserves meta)."""
        with self._lock:
            self._data.clear()

    def keys(self) -> list[str]:
        """Get all keys."""
        with self._lock:
            return list(self._data.keys())

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary (without metadata)."""
        with self._lock:
            return dict(self._data)

    def __str__(self) -> str:
        """Return JSON representation."""
        with self._lock:
            try:
                return json.dumps(self._data, ensure_ascii=False, default=str)
            except Exception as e:
                return f'{{"error": "{e}"}}'

    def __repr__(self) -> str:
        return f"Roam({self._data})"

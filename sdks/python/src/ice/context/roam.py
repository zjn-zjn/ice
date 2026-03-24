"""Thread-safe Roam implementation for Ice SDK."""

from __future__ import annotations

import io
import json
import threading
import time
from typing import Any

from ice._internal.uuid import generate_alphanum_id

# Reserved key for ice metadata
_ICE_KEY = "_ice"


class Roam:
    """
    Thread-safe dictionary for business data in ice processing.

    Supports:
    - Basic put/get/del operations
    - Deep key access (a.b.c)
    - Resolve reference (@key)
    - _ice metadata stored as a plain dict under "_ice" key
    """

    def __init__(self) -> None:
        self._data: dict[str, Any] = {}
        self._lock = threading.RLock()

    # ============ _ice convenience getters ============

    def get_meta(self) -> dict[str, Any] | None:
        """Get the _ice metadata dict."""
        ice = self._data.get(_ICE_KEY)
        return ice if isinstance(ice, dict) else None

    def get_id(self) -> int:
        """Get handler ID."""
        ice = self.get_meta()
        return int(ice.get("id", 0)) if ice else 0

    def get_scene(self) -> str:
        """Get scene."""
        ice = self.get_meta()
        return str(ice.get("scene", "")) if ice else ""

    def get_ts(self) -> int:
        """Get request timestamp."""
        ice = self.get_meta()
        return int(ice.get("ts", 0)) if ice else 0

    def get_trace(self) -> str:
        """Get trace ID."""
        ice = self.get_meta()
        return str(ice.get("trace", "")) if ice else ""

    def get_process(self) -> io.StringIO:
        """Get process StringIO for debug info."""
        ice = self.get_meta()
        if ice:
            p = ice.get("process")
            if isinstance(p, io.StringIO):
                return p
        return io.StringIO()

    def get_debug(self) -> int:
        """Get debug flag."""
        ice = self.get_meta()
        return int(ice.get("debug", 0)) if ice else 0

    def get_nid(self) -> int:
        """Get node ID."""
        ice = self.get_meta()
        return int(ice.get("nid", 0)) if ice else 0

    def get_process_info(self) -> str:
        """Get collected process information string."""
        return self.get_process().getvalue()

    # ============ _ice convenience setters ============

    def set_id(self, id: int) -> None:
        """Set handler ID."""
        self._put_meta("id", id)

    def set_scene(self, scene: str) -> None:
        """Set scene."""
        self._put_meta("scene", scene)

    def set_ts(self, ts: int) -> None:
        """Set request timestamp."""
        self._put_meta("ts", ts)

    def set_trace(self, trace: str) -> None:
        """Set trace ID."""
        self._put_meta("trace", trace)

    def set_debug(self, debug: int) -> None:
        """Set debug flag."""
        self._put_meta("debug", debug)

    def set_nid(self, nid: int) -> None:
        """Set node ID."""
        self._put_meta("nid", nid)

    def _put_meta(self, field: str, value: Any) -> None:
        """Set a field in the _ice metadata dict."""
        with self._lock:
            ice = self._data.get(_ICE_KEY)
            if isinstance(ice, dict):
                ice[field] = value

    # ============ Factory / Clone ============

    @classmethod
    def create(cls, **kwargs) -> Roam:
        """Create a Roam with default _ice metadata.

        Keyword args: id, scene, nid, ts, trace, debug.
        """
        roam = cls()
        ice: dict[str, Any] = {
            "ts": kwargs.get("ts") or int(time.time() * 1000),
            "trace": kwargs.get("trace") or generate_alphanum_id(11),
            "process": io.StringIO(),
        }
        if "id" in kwargs and kwargs["id"]:
            ice["id"] = kwargs["id"]
        if "scene" in kwargs and kwargs["scene"]:
            ice["scene"] = kwargs["scene"]
        if "nid" in kwargs and kwargs["nid"]:
            ice["nid"] = kwargs["nid"]
        if "debug" in kwargs and kwargs["debug"]:
            ice["debug"] = kwargs["debug"]
        roam._data[_ICE_KEY] = ice
        return roam

    def clone(self) -> Roam:
        """Shallow-copy data and deep-copy _ice map with a fresh process."""
        new_roam = Roam()
        with self._lock:
            for k, v in self._data.items():
                if k == _ICE_KEY and isinstance(v, dict):
                    ice_copy = v.copy()
                    ice_copy["process"] = io.StringIO()
                    new_roam._data[k] = ice_copy
                else:
                    new_roam._data[k] = v
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
        """Clear all data (preserves _ice meta)."""
        with self._lock:
            meta = self._data.get(_ICE_KEY)
            self._data.clear()
            if meta is not None:
                self._data[_ICE_KEY] = meta

    def keys(self) -> list[str]:
        """Get all keys."""
        with self._lock:
            return list(self._data.keys())

    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary."""
        with self._lock:
            result = {}
            for k, v in self._data.items():
                if k == _ICE_KEY and isinstance(v, dict):
                    # Exclude non-serializable process from output
                    d = {}
                    for ik, iv in v.items():
                        if ik == "process" and isinstance(iv, io.StringIO):
                            pv = iv.getvalue()
                            if pv:
                                d[ik] = pv
                        else:
                            d[ik] = iv
                    result[k] = d
                else:
                    result[k] = v
            return result

    def __str__(self) -> str:
        """Return JSON representation."""
        with self._lock:
            try:
                return json.dumps(self.to_dict(), ensure_ascii=False, default=str)
            except Exception as e:
                return f'{{"error": "{e}"}}'

    def __repr__(self) -> str:
        return f"Roam({self.to_dict()})"

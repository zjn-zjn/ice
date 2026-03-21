"""Thread-safe Roam implementation for Ice SDK."""

from __future__ import annotations

import io
import json
import threading
from typing import Any, TYPE_CHECKING

if TYPE_CHECKING:
    from ice.context.meta import IceMeta

# Reserved key for ice metadata
_ICE_KEY = "_ice"


class Roam:
    """
    Thread-safe dictionary for business data in ice processing.

    Supports:
    - Basic put/get operations
    - Deep key access (a.b.c)
    - Resolve reference (@key)
    - Type-specific getters
    - IceMeta stored under reserved "_ice" key
    """

    def __init__(self) -> None:
        self._data: dict[str, Any] = {}
        self._lock = threading.RLock()

    # ============ IceMeta access ============

    def get_meta(self) -> IceMeta:
        """Get the IceMeta object."""
        return self._data.get(_ICE_KEY)

    def get_ice_id(self) -> int:
        """Get ice handler ID."""
        meta = self.get_meta()
        return meta.id if meta else 0

    def get_ice_scene(self) -> str:
        """Get ice scene."""
        meta = self.get_meta()
        return meta.scene if meta else ""

    def get_ice_ts(self) -> int:
        """Get ice request timestamp."""
        meta = self.get_meta()
        return meta.ts if meta else 0

    def get_ice_trace(self) -> str:
        """Get ice trace ID."""
        meta = self.get_meta()
        return meta.trace if meta else ""

    def get_ice_process(self) -> io.StringIO:
        """Get ice process StringIO for debug info."""
        meta = self.get_meta()
        return meta.process if meta else io.StringIO()

    def get_ice_debug(self) -> int:
        """Get ice debug flag."""
        meta = self.get_meta()
        return meta.debug if meta else 0

    def get_process_info(self) -> str:
        """Get collected process information string."""
        meta = self.get_meta()
        return meta.get_process_info() if meta else ""

    # ============ Factory / Clone ============

    @classmethod
    def create(cls, **kwargs) -> Roam:
        """Create a Roam with a default IceMeta.

        Keyword args are forwarded to IceMeta constructor.
        """
        from ice.context.meta import IceMeta
        roam = cls()
        roam._data[_ICE_KEY] = IceMeta(**kwargs)
        return roam

    def clone(self) -> Roam:
        """Shallow-copy data and clone IceMeta with a fresh process."""
        new_roam = Roam()
        with self._lock:
            new_roam._data = self._data.copy()
        meta = new_roam._data.get(_ICE_KEY)
        if meta is not None:
            new_roam._data[_ICE_KEY] = meta.clone()
        return new_roam

    # Keep shallow_copy as alias for backward compatibility
    def shallow_copy(self) -> Roam:
        """Create a shallow copy for parallel execution (alias for clone)."""
        return self.clone()

    # ============ Put operations ============

    def put(self, key: str, value: Any) -> Roam:
        """Put a value into roam. Silently ignores writes to '_ice' key."""
        if key == _ICE_KEY:
            return self
        with self._lock:
            self._data[key] = value
        return self

    def put_direct(self, key: str, value: Any) -> Roam:
        """Put a value including reserved keys (internal use only)."""
        with self._lock:
            self._data[key] = value
        return self

    def put_all(self, data: dict[str, Any]) -> Roam:
        """Put multiple key-value pairs at once. Skips '_ice' key."""
        with self._lock:
            for k, v in data.items():
                if k != _ICE_KEY:
                    self._data[k] = v
        return self

    def put_deep(self, multi_key: str, value: Any) -> Any:
        """
        Put value using deep key (e.g., 'a.b.c').
        Supports storing None values.

        Example:
            roam.put_deep("user.profile.age", 25)
            # Creates nested structure: {"user": {"profile": {"age": 25}}}
        """
        if multi_key is None:
            return None

        keys = multi_key.split(".")
        if len(keys) == 1:
            if keys[0] == _ICE_KEY:
                return None
            with self._lock:
                old = self._data.get(keys[0])
                self._data[keys[0]] = value
                return old

        if keys[0] == _ICE_KEY:
            return None

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
        """
        Get value using deep key (e.g., 'a.b.c').

        Example:
            roam.put("user", {"name": "test", "age": 18})
            roam.get_deep("user.name")  # "test"
        """
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
        """
        Resolve reference.

        If value starts with '@', treat it as a reference to another key.
        Example:
            roam.put("score", 100)
            roam.put("ref", "@score")
            roam.resolve(roam.get("ref"))  # 100
        """
        if isinstance(value, str) and value.startswith("@"):
            ref_key = value[1:]
            if "." in ref_key:
                return self.get_deep(ref_key)
            return self.get(ref_key)
        return value

    # ============ Utility operations ============

    def contains(self, key: str) -> bool:
        """Check if key exists."""
        with self._lock:
            return key in self._data

    def remove(self, key: str) -> Any:
        """Remove a key and return its value."""
        if key == _ICE_KEY:
            return None
        with self._lock:
            return self._data.pop(key, None)

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
                if k == _ICE_KEY and hasattr(v, 'to_dict'):
                    result[k] = v.to_dict()
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

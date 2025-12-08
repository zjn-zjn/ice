"""Thread-safe Roam implementation for Ice SDK."""

from __future__ import annotations

import json
import threading
from typing import Any


class Roam:
    """
    Thread-safe dictionary for business data in ice processing.
    
    Supports:
    - Basic put/get operations
    - Multi-level key access (a.b.c)
    - Union reference (@key)
    - Type-specific getters
    """
    
    def __init__(self) -> None:
        self._data: dict[str, Any] = {}
        self._lock = threading.RLock()
    
    def put(self, key: str, value: Any) -> Roam:
        """Put a value into roam."""
        with self._lock:
            self._data[key] = value
        return self
    
    def put_all(self, data: dict[str, Any]) -> Roam:
        """Put multiple key-value pairs at once."""
        with self._lock:
            self._data.update(data)
        return self
    
    def put_multi(self, multi_key: str, value: Any) -> Any:
        """
        Put value using multi-level key (e.g., 'a.b.c').
        
        This matches Java's putMulti behavior.
        Example:
            roam.put_multi("user.profile.age", 25)
            # Creates nested structure: {"user": {"profile": {"age": 25}}}
        """
        if multi_key is None or value is None:
            return None
        
        keys = multi_key.split(".")
        if len(keys) == 1:
            with self._lock:
                old = self._data.get(keys[0])
                self._data[keys[0]] = value
                return old
        
        with self._lock:
            end_map = self._data
            for i in range(len(keys) - 1):
                if keys[i] not in end_map:
                    end_map[keys[i]] = {}
                next_map = end_map.get(keys[i])
                if not isinstance(next_map, dict):
                    end_map[keys[i]] = {}
                    next_map = end_map[keys[i]]
                end_map = next_map
            old = end_map.get(keys[-1])
            end_map[keys[-1]] = value
            return old
    
    def get(self, key: str, default: Any = None) -> Any:
        """Get a value from roam."""
        with self._lock:
            return self._data.get(key, default)
    
    def get_multi(self, key: str, default: Any = None) -> Any:
        """
        Get value using multi-level key (e.g., 'a.b.c').
        
        Example:
            roam.put("user", {"name": "test", "age": 18})
            roam.get_multi("user.name")  # "test"
        """
        with self._lock:
            keys = key.split(".")
            value = self._data
            for k in keys:
                if isinstance(value, dict):
                    value = value.get(k)
                    if value is None:
                        return default
                else:
                    return default
            return value
    
    def get_union(self, value: Any) -> Any:
        """
        Resolve union reference.
        
        If value starts with '@', treat it as a reference to another key.
        Example:
            roam.put("score", 100)
            roam.put("ref", "@score")
            roam.get_union(roam.get("ref"))  # 100
        """
        if isinstance(value, str) and value.startswith("@"):
            ref_key = value[1:]
            if "." in ref_key:
                return self.get_multi(ref_key)
            return self.get(ref_key)
        return value
    
    def get_str(self, key: str, default: str = "") -> str:
        """Get value as string."""
        value = self.get(key)
        if value is None:
            return default
        return str(value)
    
    def get_int(self, key: str, default: int = 0) -> int:
        """Get value as int."""
        value = self.get(key)
        if value is None:
            return default
        try:
            return int(value)
        except (ValueError, TypeError):
            return default
    
    def get_float(self, key: str, default: float = 0.0) -> float:
        """Get value as float."""
        value = self.get(key)
        if value is None:
            return default
        try:
            return float(value)
        except (ValueError, TypeError):
            return default
    
    def get_bool(self, key: str, default: bool = False) -> bool:
        """Get value as bool."""
        value = self.get(key)
        if value is None:
            return default
        if isinstance(value, bool):
            return value
        if isinstance(value, str):
            return value.lower() in ("true", "1", "yes")
        return bool(value)
    
    def get_list(self, key: str) -> list[Any]:
        """Get value as list."""
        value = self.get(key)
        if isinstance(value, list):
            return value
        return []
    
    def get_dict(self, key: str) -> dict[str, Any]:
        """Get value as dict."""
        value = self.get(key)
        if isinstance(value, dict):
            return value
        return {}
    
    def contains(self, key: str) -> bool:
        """Check if key exists."""
        with self._lock:
            return key in self._data
    
    def remove(self, key: str) -> Any:
        """Remove a key and return its value."""
        with self._lock:
            return self._data.pop(key, None)
    
    def clear(self) -> None:
        """Clear all data."""
        with self._lock:
            self._data.clear()
    
    def keys(self) -> list[str]:
        """Get all keys."""
        with self._lock:
            return list(self._data.keys())
    
    def shallow_copy(self) -> Roam:
        """Create a shallow copy for parallel execution."""
        new_roam = Roam()
        with self._lock:
            new_roam._data = self._data.copy()
        return new_roam
    
    def to_dict(self) -> dict[str, Any]:
        """Convert to dictionary."""
        with self._lock:
            return self._data.copy()
    
    def __str__(self) -> str:
        """Return JSON representation."""
        with self._lock:
            try:
                return json.dumps(self._data, ensure_ascii=False, default=str)
            except Exception as e:
                return f"{{\"error\": \"{e}\"}}"
    
    def __repr__(self) -> str:
        return f"Roam({self._data})"


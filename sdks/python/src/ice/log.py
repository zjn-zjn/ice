"""Logging module for Ice SDK."""

from __future__ import annotations

import contextvars
import json
import logging
from abc import ABC, abstractmethod
from typing import Any

# Context variable for trace ID (like Java MDC / Go context)
_trace_id_var: contextvars.ContextVar[str] = contextvars.ContextVar('traceId', default='')


def set_trace_id(trace_id: str) -> contextvars.Token[str]:
    """Set trace ID in current context. Returns token for reset."""
    return _trace_id_var.set(trace_id)


def get_trace_id() -> str:
    """Get trace ID from current context."""
    return _trace_id_var.get()


def reset_trace_id(token: contextvars.Token[str]) -> None:
    """Reset trace ID to previous value."""
    _trace_id_var.reset(token)


class Logger(ABC):
    """Abstract logger interface for Ice SDK."""
    
    @abstractmethod
    def debug(self, msg: str, **kwargs: Any) -> None:
        """Log debug message."""
        pass
    
    @abstractmethod
    def info(self, msg: str, **kwargs: Any) -> None:
        """Log info message."""
        pass
    
    @abstractmethod
    def warn(self, msg: str, **kwargs: Any) -> None:
        """Log warning message."""
        pass
    
    @abstractmethod
    def error(self, msg: str, **kwargs: Any) -> None:
        """Log error message."""
        pass


class DefaultLogger(Logger):
    """Default logger implementation using Python's logging module."""
    
    def __init__(self) -> None:
        self._logger = logging.getLogger("ice")
    
    def _trace_prefix(self) -> str:
        trace_id = _trace_id_var.get()
        return f"[{trace_id}] " if trace_id else ""

    def _format_kwargs(self, kwargs: dict[str, Any]) -> str:
        """Format kwargs as key=value pairs, converting complex objects to JSON."""
        if not kwargs:
            return ""
        parts = []
        for key, value in kwargs.items():
            if isinstance(value, (dict, list)):
                try:
                    value = json.dumps(value, ensure_ascii=False, default=str)
                except Exception:
                    value = str(value)
            elif hasattr(value, "to_dict"):
                try:
                    value = json.dumps(value.to_dict(), ensure_ascii=False, default=str)
                except Exception:
                    value = str(value)
            parts.append(f"{key}={value}")
        return " " + " ".join(parts)
    
    def debug(self, msg: str, **kwargs: Any) -> None:
        self._logger.debug(self._trace_prefix() + msg + self._format_kwargs(kwargs))

    def info(self, msg: str, **kwargs: Any) -> None:
        self._logger.info(self._trace_prefix() + msg + self._format_kwargs(kwargs))

    def warn(self, msg: str, **kwargs: Any) -> None:
        self._logger.warning(self._trace_prefix() + msg + self._format_kwargs(kwargs))

    def error(self, msg: str, **kwargs: Any) -> None:
        self._logger.error(self._trace_prefix() + msg + self._format_kwargs(kwargs))


# Global logger instance
_logger: Logger = DefaultLogger()


def get_logger() -> Logger:
    """Get the global logger."""
    return _logger


def set_logger(logger: Logger) -> None:
    """Set the global logger."""
    global _logger
    _logger = logger


def debug(msg: str, **kwargs: Any) -> None:
    """Log debug message."""
    _logger.debug(msg, **kwargs)


def info(msg: str, **kwargs: Any) -> None:
    """Log info message."""
    _logger.info(msg, **kwargs)


def warn(msg: str, **kwargs: Any) -> None:
    """Log warning message."""
    _logger.warn(msg, **kwargs)


def error(msg: str, **kwargs: Any) -> None:
    """Log error message."""
    _logger.error(msg, **kwargs)


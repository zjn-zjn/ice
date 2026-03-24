"""Logging module for Ice SDK.

Uses Python's standard logging module. Users configure logging via the standard
logging API (handlers, formatters, filters on the "ice" logger).

Example:
    import logging
    logging.getLogger("ice").addHandler(my_handler)
    logging.getLogger("ice").setLevel(logging.DEBUG)

TraceId is injected into every log record as record.traceId, accessible
in formatters via %(traceId)s.
"""

from __future__ import annotations

import contextvars
import logging

_trace_id_var: contextvars.ContextVar[str] = contextvars.ContextVar('traceId', default='')

_logger = logging.getLogger("ice")


def set_trace_id(trace_id: str) -> contextvars.Token[str]:
    """Set trace ID in current context. Returns token for reset."""
    return _trace_id_var.set(trace_id)


def get_trace_id() -> str:
    """Get trace ID from current context."""
    return _trace_id_var.get()


def reset_trace_id(token: contextvars.Token[str]) -> None:
    """Reset trace ID to previous value."""
    _trace_id_var.reset(token)


class _TraceFilter(logging.Filter):
    """Injects traceId into log records as a structured field."""

    def filter(self, record: logging.LogRecord) -> bool:
        record.traceId = _trace_id_var.get()  # type: ignore[attr-defined]
        return True


_logger.addFilter(_TraceFilter())


def debug(msg: str, *args, **kwargs) -> None:
    """Log debug message."""
    _logger.debug(msg, *args, **kwargs)


def info(msg: str, *args, **kwargs) -> None:
    """Log info message."""
    _logger.info(msg, *args, **kwargs)


def warn(msg: str, *args, **kwargs) -> None:
    """Log warning message."""
    _logger.warning(msg, *args, **kwargs)


def error(msg: str, *args, **kwargs) -> None:
    """Log error message."""
    _logger.error(msg, *args, **kwargs)

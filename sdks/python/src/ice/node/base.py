"""Base node implementation for Ice SDK."""

from __future__ import annotations

import time
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Callable, Protocol, runtime_checkable

from ice.enums import RunState, NodeType, TimeType
from ice._internal.timeutil import time_disabled

if TYPE_CHECKING:
    from ice.context.context import Context


# Type for global error handler function
GlobalErrorHandler = Callable[["Node | None", "Context", Exception], RunState]

# Global error handler - can be set by user
_global_error_handler: GlobalErrorHandler | None = None


def set_global_error_handler(handler: GlobalErrorHandler | None) -> None:
    """
    Set a custom global error handler.
    
    This handler is called when a node does not implement error_handle method.
    If not set, the default behavior is to return SHUT_DOWN (re-raise exception).
    
    Args:
        handler: A function that takes (node, context, exception) and returns RunState.
                 Pass None to clear the handler.
    """
    global _global_error_handler
    _global_error_handler = handler


def get_global_error_handler() -> GlobalErrorHandler | None:
    """Get the current global error handler."""
    return _global_error_handler


@runtime_checkable
class ErrorHandler(Protocol):
    """
    Protocol for nodes that implement custom error handling.
    
    Implement the error_handle method in your leaf node to handle errors:
    
        class MyLeaf:
            def error_handle(self, ctx: Context, error: Exception) -> RunState:
                print(f"Error occurred: {error}")
                return RunState.FALSE  # Continue with FALSE instead of crashing
    """
    def error_handle(self, ctx: "Context", error: Exception) -> RunState:
        ...


class Node(ABC):
    """Abstract base class for all ice nodes."""
    
    @abstractmethod
    def process(self, ctx: Context) -> RunState:
        """Process the node and return the result state."""
        pass
    
    @abstractmethod
    def get_node_id(self) -> int:
        """Get the node ID."""
        pass
    
    @abstractmethod
    def get_log_name(self) -> str:
        """Get the log name for debug output."""
        pass
    
    @abstractmethod
    def is_debug(self) -> bool:
        """Check if debug is enabled for this node."""
        pass
    
    @abstractmethod
    def is_inverse(self) -> bool:
        """Check if result should be inverted."""
        pass


class Base:
    """
    Base contains common fields for all node types.
    
    This is equivalent to Go's node.Base struct.
    """
    
    def __init__(self) -> None:
        self.ice_node_id: int = 0
        self.ice_time_type: TimeType = TimeType.NONE
        self.ice_start: int = 0
        self.ice_end: int = 0
        self.ice_node_debug: bool = False
        self.ice_inverse: bool = False
        self.ice_forward: Node | None = None
        self.ice_error_state: RunState | None = None
        self.ice_log_name: str = ""
        self.ice_type: NodeType = NodeType.LEAF_NONE
    
    def get_node_id(self) -> int:
        return self.ice_node_id
    
    def get_log_name(self) -> str:
        return self.ice_log_name
    
    def is_debug(self) -> bool:
        return self.ice_node_debug
    
    def is_inverse(self) -> bool:
        return self.ice_inverse
    
    def get_base(self) -> Base:
        """Get self for BaseAccessor interface."""
        return self
    
    def set_forward(self, forward: Node | None) -> None:
        """Set the forward node."""
        self.ice_forward = forward


def _inverse(state: RunState) -> RunState:
    """Inverse TRUE to FALSE and vice versa. NONE and SHUT_DOWN are unchanged."""
    if state == RunState.TRUE:
        return RunState.FALSE
    elif state == RunState.FALSE:
        return RunState.TRUE
    return state


def _state_to_char(state: RunState) -> str:
    """Convert RunState to a single character for debug output."""
    if state == RunState.FALSE:
        return "F"
    elif state == RunState.TRUE:
        return "T"
    elif state == RunState.NONE:
        return "N"
    elif state == RunState.SHUT_DOWN:
        return "S"
    return "?"


def collect_info(ctx: Context, base: Base, state: str, time_ms: int) -> None:
    """Collect debug information for a node execution."""
    if base is None or not base.ice_node_debug:
        return
    inverse_str = "-I" if base.ice_inverse else ""
    ctx.process_info.write(
        f"[{base.ice_node_id}:{base.ice_log_name}:{state}{inverse_str}:{time_ms}]"
    )


def collect_reject_info(ctx: Context, base: Base) -> None:
    """Collect debug information for a rejected node (forward returned false)."""
    if base is None or not base.ice_node_debug:
        return
    ctx.process_info.write(f"[{base.ice_node_id}:{base.ice_log_name}:R-F]")


def process_with_base(
    base: Base,
    ctx: Context,
    process_node: Callable[[Context], RunState],
    error_handler: ErrorHandler | None = None,
) -> RunState:
    """
    Execute common node logic and delegate to the specific process_node function.
    
    This implements the template method pattern, equivalent to Go's ProcessWithBase.
    
    Args:
        base: The node's base properties
        ctx: The execution context
        process_node: The specific node processing function
        error_handler: Optional error handler (leaf node instance that implements error_handle)
    
    Returns:
        The final RunState after processing
    """
    # Time check
    if time_disabled(base.ice_time_type, ctx.pack.request_time, base.ice_start, base.ice_end):
        collect_info(ctx, base, "O", 0)
        return RunState.NONE
    
    start = int(time.time() * 1000)
    result: RunState
    
    try:
        # Forward check
        if base.ice_forward is not None:
            forward_res = base.ice_forward.process(ctx)
            if forward_res == RunState.FALSE:
                collect_reject_info(ctx, base)
                return RunState.FALSE
            
            result = process_node(ctx)
            # Forward combines like AND relation
            if forward_res == RunState.NONE:
                pass  # result stays as is
            elif result == RunState.NONE:
                result = RunState.TRUE
        else:
            result = process_node(ctx)
        
        elapsed = int(time.time() * 1000) - start
        collect_info(ctx, base, _state_to_char(result), elapsed)
        
        # Inverse
        if base.ice_inverse:
            result = _inverse(result)
        
        return result
        
    except Exception as e:
        elapsed = int(time.time() * 1000) - start
        
        # 1. First try node-level error handler
        error_state: RunState = RunState.SHUT_DOWN
        if error_handler is not None and hasattr(error_handler, 'error_handle'):
            error_state = error_handler.error_handle(ctx, e)
        elif _global_error_handler is not None:
            # 2. Fall back to global error handler
            error_state = _global_error_handler(None, ctx, e)
        
        # 3. Config-level error state has highest priority
        if base.ice_error_state is not None:
            error_state = base.ice_error_state
        
        if error_state != RunState.SHUT_DOWN:
            collect_info(ctx, base, _state_to_char(error_state), elapsed)
            # Apply inverse even on error
            if base.ice_inverse:
                error_state = _inverse(error_state)
            return error_state
        
        # SHUT_DOWN - re-raise
        collect_info(ctx, base, "S", elapsed)
        raise


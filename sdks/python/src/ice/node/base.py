"""Base node implementation for Ice SDK."""

from __future__ import annotations

import time
from abc import ABC, abstractmethod
from typing import TYPE_CHECKING, Callable

from ice.enums import RunState, NodeType, TimeType
from ice._internal.timeutil import time_disabled

if TYPE_CHECKING:
    from ice.context.context import Context


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
) -> RunState:
    """
    Execute common node logic and delegate to the specific process_node function.
    
    This implements the template method pattern, equivalent to Go's ProcessWithBase.
    
    Args:
        base: The node's base properties
        ctx: The execution context
        process_node: The specific node processing function
    
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
        
        # Error handling
        if base.ice_error_state is not None:
            error_state = base.ice_error_state
            if error_state != RunState.SHUT_DOWN:
                collect_info(ctx, base, _state_to_char(error_state), elapsed)
                # Apply inverse even on error
                if base.ice_inverse:
                    error_state = _inverse(error_state)
                return error_state
        
        # SHUT_DOWN or no error state configured - re-raise
        collect_info(ctx, base, "S", elapsed)
        raise


"""Thread pool executor for parallel nodes in Ice SDK."""

from __future__ import annotations

import os
from concurrent.futures import ThreadPoolExecutor, Future
from dataclasses import dataclass
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from ice.context.context import Context
    from ice.node.base import Node
    from ice.enums import RunState


# Global executor
_executor: ThreadPoolExecutor | None = None


def init_executor(parallelism: int = -1) -> None:
    """
    Initialize the global thread pool executor.
    
    Args:
        parallelism: Number of worker threads. -1 means use CPU count.
    """
    global _executor
    if _executor is not None:
        return
    
    if parallelism <= 0:
        parallelism = os.cpu_count() or 4
    
    _executor = ThreadPoolExecutor(max_workers=parallelism, thread_name_prefix="ice-worker")


def shutdown_executor() -> None:
    """Shutdown the global executor."""
    global _executor
    if _executor is not None:
        _executor.shutdown(wait=True)
        _executor = None


def get_executor() -> ThreadPoolExecutor:
    """Get the global executor, initializing if needed."""
    global _executor
    if _executor is None:
        init_executor()
    return _executor  # type: ignore


@dataclass
class NodeResult:
    """Result of a node execution."""
    state: RunState
    error: Exception | None = None


def submit_node(node: Node, ctx: Context) -> Future[NodeResult]:
    """
    Submit a node for execution in the thread pool.
    
    Args:
        node: The node to execute
        ctx: The execution context (should be a copy for parallel execution)
    
    Returns:
        A Future containing the NodeResult
    """
    from ice.enums import RunState
    
    def execute() -> NodeResult:
        try:
            state = node.process(ctx)
            return NodeResult(state=state)
        except Exception as e:
            return NodeResult(state=RunState.SHUT_DOWN, error=e)
    
    executor = get_executor()
    return executor.submit(execute)


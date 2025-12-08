"""Parallel relation nodes for Ice SDK."""

from __future__ import annotations

from concurrent.futures import as_completed
from typing import TYPE_CHECKING

from ice.enums import RunState
from ice.node.base import Node, process_with_base
from ice.node.relation import Relation
from ice._internal.executor import submit_node

if TYPE_CHECKING:
    from ice.context.context import Context


class ParallelAnd(Relation, Node):
    """
    Parallel And relation node - executes children in parallel.
    
    Returns FALSE as soon as any child returns FALSE.
    - Has FALSE -> FALSE
    - Without FALSE, has TRUE -> TRUE
    - All NONE -> NONE
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "P-And"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        # Submit all children for parallel execution
        futures = []
        for child in self.children:
            # Clone the context for parallel execution
            child_ctx = _clone_context(ctx)
            futures.append(submit_node(child, child_ctx))
        
        has_true = False
        
        # Wait for results
        for future in as_completed(futures):
            result = future.result()
            if result.error:
                return RunState.SHUT_DOWN
            if result.state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
            if result.state == RunState.FALSE:
                return RunState.FALSE
            if result.state == RunState.TRUE:
                has_true = True
        
        return RunState.TRUE if has_true else RunState.NONE


class ParallelAny(Relation, Node):
    """
    Parallel Any relation node - executes children in parallel.
    
    Returns TRUE as soon as any child returns TRUE.
    - Has TRUE -> TRUE
    - Without TRUE, has FALSE -> FALSE
    - All NONE -> NONE
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "P-Any"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        futures = []
        for child in self.children:
            child_ctx = _clone_context(ctx)
            futures.append(submit_node(child, child_ctx))
        
        has_false = False
        
        for future in as_completed(futures):
            result = future.result()
            if result.error:
                return RunState.SHUT_DOWN
            if result.state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
            if result.state == RunState.TRUE:
                return RunState.TRUE
            if result.state == RunState.FALSE:
                has_false = True
        
        return RunState.FALSE if has_false else RunState.NONE


class ParallelAll(Relation, Node):
    """
    Parallel All relation node - executes all children in parallel.
    
    Waits for all children to complete.
    - Has SHUT_DOWN -> SHUT_DOWN
    - Has FALSE -> FALSE
    - Has TRUE -> TRUE
    - All NONE -> NONE
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "P-All"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        futures = []
        for child in self.children:
            child_ctx = _clone_context(ctx)
            futures.append(submit_node(child, child_ctx))
        
        has_true = False
        has_false = False
        
        for future in as_completed(futures):
            result = future.result()
            if result.error:
                return RunState.SHUT_DOWN
            if result.state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
            if result.state == RunState.TRUE:
                has_true = True
            elif result.state == RunState.FALSE:
                has_false = True
        
        if has_false:
            return RunState.FALSE
        if has_true:
            return RunState.TRUE
        return RunState.NONE


class ParallelTrue(Relation, Node):
    """
    Parallel True relation node - executes all children in parallel.
    
    Always returns TRUE after all children complete.
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "P-True"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.TRUE
        
        futures = []
        for child in self.children:
            child_ctx = _clone_context(ctx)
            futures.append(submit_node(child, child_ctx))
        
        for future in as_completed(futures):
            result = future.result()
            if result.error:
                return RunState.SHUT_DOWN
            if result.state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
        
        return RunState.TRUE


class ParallelNone(Relation, Node):
    """
    Parallel None relation node - executes all children in parallel.
    
    Always returns NONE after all children complete.
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "P-None"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        futures = []
        for child in self.children:
            child_ctx = _clone_context(ctx)
            futures.append(submit_node(child, child_ctx))
        
        for future in as_completed(futures):
            result = future.result()
            if result.error:
                return RunState.SHUT_DOWN
            if result.state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
        
        return RunState.NONE


def _clone_context(ctx: Context) -> Context:
    """Clone a context for parallel execution."""
    from ice.context.context import Context as ContextClass
    
    # Clone pack with shallow copy of roam
    cloned_pack = ctx.pack.clone()
    new_ctx = ContextClass(ctx.ice_id, cloned_pack)
    return new_ctx


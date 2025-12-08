"""Serial relation nodes for Ice SDK."""

from __future__ import annotations

from typing import TYPE_CHECKING

from ice.enums import RunState
from ice.node.base import Node, process_with_base
from ice.node.relation import Relation

if TYPE_CHECKING:
    from ice.context.context import Context


class And(Relation, Node):
    """
    And relation node - returns FALSE on first FALSE.
    
    - Has FALSE -> FALSE
    - Without FALSE, has TRUE -> TRUE
    - Without children -> NONE
    - All NONE -> NONE
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "And"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        has_true = False
        for child in self.children:
            state = child.process(ctx)
            if state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
            if state == RunState.FALSE:
                return RunState.FALSE
            if state == RunState.TRUE:
                has_true = True
        
        return RunState.TRUE if has_true else RunState.NONE


class Any(Relation, Node):
    """
    Any relation node - returns TRUE on first TRUE.
    
    - Has TRUE -> TRUE
    - Without TRUE, has FALSE -> FALSE
    - Without children -> NONE
    - All NONE -> NONE
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "Any"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        has_false = False
        for child in self.children:
            state = child.process(ctx)
            if state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
            if state == RunState.TRUE:
                return RunState.TRUE
            if state == RunState.FALSE:
                has_false = True
        
        return RunState.FALSE if has_false else RunState.NONE


class All(Relation, Node):
    """
    All relation node - executes all children and returns combined result.
    
    - Has SHUT_DOWN -> SHUT_DOWN
    - Has FALSE -> FALSE
    - Has TRUE -> TRUE
    - All NONE -> NONE
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "All"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        if not self.children:
            return RunState.NONE
        
        has_true = False
        has_false = False
        
        for child in self.children:
            state = child.process(ctx)
            if state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
            if state == RunState.TRUE:
                has_true = True
            elif state == RunState.FALSE:
                has_false = True
        
        if has_false:
            return RunState.FALSE
        if has_true:
            return RunState.TRUE
        return RunState.NONE


class TrueNode(Relation, Node):
    """
    True relation node - executes all children and always returns TRUE.
    
    Named TrueNode to avoid conflict with Python's True keyword.
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "True"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        for child in self.children:
            state = child.process(ctx)
            if state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
        return RunState.TRUE


class NoneNode(Relation, Node):
    """
    None relation node - executes all children and always returns NONE.
    
    Named NoneNode to avoid conflict with Python's None keyword.
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.ice_log_name = "None"
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_process)
    
    def _do_process(self, ctx: Context) -> RunState:
        for child in self.children:
            state = child.process(ctx)
            if state == RunState.SHUT_DOWN:
                return RunState.SHUT_DOWN
        return RunState.NONE


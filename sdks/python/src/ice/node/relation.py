"""Relation node base implementation for Ice SDK."""

from __future__ import annotations

from typing import TYPE_CHECKING

from ice.node.base import Base, Node

if TYPE_CHECKING:
    pass


class Relation(Base):
    """
    Relation is the base type for relation nodes that contain children.
    
    Relation nodes control the execution flow of their children.
    """
    
    def __init__(self) -> None:
        super().__init__()
        self.children: list[Node] = []
        self.son_ids: list[int] = []
    
    def get_children(self) -> list[Node]:
        """Get the children list."""
        return self.children
    
    def set_children(self, children: list[Node]) -> None:
        """Set the children list."""
        self.children = children
    
    def get_son_ids(self) -> list[int]:
        """Get the son IDs."""
        return self.son_ids
    
    def set_son_ids(self, ids: list[int]) -> None:
        """Set the son IDs."""
        self.son_ids = ids
    
    def add_child(self, child: Node) -> None:
        """Add a child node."""
        self.children.append(child)
    
    def remove_child(self, child: Node) -> None:
        """Remove a child node."""
        if child in self.children:
            self.children.remove(child)
    
    def clear_children(self) -> None:
        """Clear all children."""
        self.children.clear()


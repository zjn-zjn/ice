"""Enums for Ice SDK, compatible with Java/Go."""

from enum import IntEnum


class RunState(IntEnum):
    """Node execution result state."""
    FALSE = 1
    TRUE = 2
    NONE = 3
    SHUT_DOWN = 4


class NodeType(IntEnum):
    """Node type enum - values must match Java/Go."""
    # Relation nodes (serial)
    NONE = 0
    AND = 1
    TRUE = 2
    ALL = 3
    ANY = 4
    # Leaf nodes
    LEAF_FLOW = 5
    LEAF_RESULT = 6
    LEAF_NONE = 7
    # Parallel relation nodes
    P_NONE = 8
    P_AND = 9
    P_TRUE = 10
    P_ALL = 11
    P_ANY = 12
    
    def is_relation(self) -> bool:
        """Check if this node type is a relation node (serial or parallel)."""
        return self in (
            NodeType.NONE, NodeType.AND, NodeType.TRUE, NodeType.ALL, NodeType.ANY,
            NodeType.P_NONE, NodeType.P_AND, NodeType.P_TRUE, NodeType.P_ALL, NodeType.P_ANY
        )
    
    def is_leaf(self) -> bool:
        """Check if this node type is a leaf node."""
        return self in (NodeType.LEAF_FLOW, NodeType.LEAF_RESULT, NodeType.LEAF_NONE)


class TimeType(IntEnum):
    """Time control type."""
    NONE = 0
    BETWEEN = 1
    AFTER_START = 2
    BEFORE_END = 3


class RequestType(IntEnum):
    """Request type enum."""
    FORMAL = 0
    MOCK = 1


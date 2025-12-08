"""Leaf node registration and auto-adaptation for Ice SDK."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any, Callable, Type, get_type_hints, get_origin, get_args

from ice.dto import LeafNodeInfo, IceFieldInfo
from ice.enums import NodeType, RunState
from ice.node.base import Base, Node, process_with_base
from ice.node.leaf import Leaf

# For Annotated support
try:
    from typing import Annotated, get_origin, get_args
except ImportError:
    from typing_extensions import Annotated, get_origin, get_args

if TYPE_CHECKING:
    from ice.context.context import Context
    from ice.context.pack import Pack
    from ice.context.roam import Roam


@dataclass
class IceField:
    """
    Field metadata for ice fields.
    
    Use with typing.Annotated to mark fields as ice fields (visible in UI):
    
        class ScoreFlow:
            score: Annotated[int, IceField(name="分数阈值", desc="判断分数")] = 0
            key: Annotated[str, IceField(name="键名")] = "score"
    """
    name: str = ""
    desc: str = ""


@dataclass
class IceIgnore:
    """
    Marker to ignore a field completely (not in iceFields or hideFields).
    
    Use with typing.Annotated:
    
        class MyNode:
            _service: SomeService  # Already ignored by _ prefix
            cache: Annotated[dict, IceIgnore()] = None  # Explicitly ignored
    """
    pass


# Alias for backward compatibility
FieldMeta = IceField


@dataclass
class LeafMeta:
    """Metadata for a leaf node."""
    name: str = ""
    desc: str = ""
    order: int = 100
    # Aliases for multi-language compatibility
    # When config's confName matches an alias, it maps to this class
    alias: list[str] = field(default_factory=list)
    # Optional: explicitly define iceFields with metadata (legacy way)
    ice_fields: dict[str, IceField] | None = None


@dataclass
class LeafEntry:
    """Registration entry for a leaf type."""
    cls: Type
    meta: LeafMeta | None
    node_type: NodeType
    ice_fields: list[IceFieldInfo]
    hide_fields: list[IceFieldInfo]


# Global registry: class_name -> LeafEntry
_registry: dict[str, LeafEntry] = {}
# Alias registry: alias -> class_name
_alias_registry: dict[str, str] = {}


def _get_type_name(py_type: Any) -> str:
    """Convert Python type to language-agnostic type name."""
    # Handle Annotated types - extract the actual type
    origin = get_origin(py_type)
    if origin is Annotated:
        args = get_args(py_type)
        if args:
            py_type = args[0]
            origin = get_origin(py_type)
    
    if py_type is str:
        return "string"
    elif py_type is int:
        return "long"
    elif py_type is float:
        return "double"
    elif py_type is bool:
        return "boolean"
    elif origin is list:
        return "list"
    elif origin is dict:
        return "map"
    return "object"


def _extract_fields(cls: Type, meta: LeafMeta | None) -> tuple[list[IceFieldInfo], list[IceFieldInfo]]:
    """
    Extract field information from a class using annotations.
    
    Supports two ways to define ice fields:
    1. Using Annotated: `score: Annotated[int, IceField(name="xxx")]`
    2. Using meta.ice_fields dict (legacy)
    """
    ice_fields: list[IceFieldInfo] = []
    hide_fields: list[IceFieldInfo] = []
    
    # Get type hints including Annotated metadata
    try:
        hints = get_type_hints(cls, include_extras=True)
    except Exception:
        hints = getattr(cls, "__annotations__", {})
    
    # Get legacy iceFields metadata from LeafMeta if provided
    legacy_ice_fields = meta.ice_fields if meta and meta.ice_fields else {}
    
    for field_name, field_type in hints.items():
        # Skip private fields
        if field_name.startswith("_"):
            continue
        
        # Check if this is an Annotated type with IceField or IceIgnore
        ice_field_meta: IceField | None = None
        is_ignored = False
        actual_type = field_type
        
        origin = get_origin(field_type)
        if origin is Annotated:
            args = get_args(field_type)
            if args:
                actual_type = args[0]
                # Look for IceField or IceIgnore in the annotations
                for arg in args[1:]:
                    if isinstance(arg, IceIgnore):
                        is_ignored = True
                        break
                    if isinstance(arg, IceField):
                        ice_field_meta = arg
        
        # Skip ignored fields
        if is_ignored:
            continue
        
        # Fall back to legacy meta.ice_fields
        if ice_field_meta is None and field_name in legacy_ice_fields:
            ice_field_meta = legacy_ice_fields[field_name]
        
        type_name = _get_type_name(actual_type)
        
        if ice_field_meta is not None:
            # This is an iceField
            ice_fields.append(IceFieldInfo(
                field=field_name,
                name=ice_field_meta.name,
                desc=ice_field_meta.desc,
                type=type_name,
            ))
        else:
            # This is a hideField
            hide_fields.append(IceFieldInfo(
                field=field_name,
                type=type_name,
            ))
    
    return ice_fields, hide_fields


def _detect_node_type(cls: Type) -> NodeType:
    """Detect the node type based on which methods the class implements."""
    # Check Flow types
    if hasattr(cls, "do_roam_flow") or hasattr(cls, "do_pack_flow") or hasattr(cls, "do_flow"):
        return NodeType.LEAF_FLOW
    # Check Result types
    if hasattr(cls, "do_roam_result") or hasattr(cls, "do_pack_result") or hasattr(cls, "do_result"):
        return NodeType.LEAF_RESULT
    # Check None types
    if hasattr(cls, "do_roam_none") or hasattr(cls, "do_pack_none") or hasattr(cls, "do_none"):
        return NodeType.LEAF_NONE
    # Default to None type
    return NodeType.LEAF_NONE


def leaf(
    class_name: str,
    *,
    name: str = "",
    desc: str = "",
    order: int = 100,
    alias: list[str] | str | None = None,
    ice_fields: dict[str, IceField] | None = None,
) -> Callable[[Type], Type]:
    """
    Decorator to register a leaf node class.
    
    Usage with Annotated (recommended):
        @ice.leaf("com.example.ScoreFlow", name="分数判断", alias=["score_flow"])
        class ScoreFlow:
            score: Annotated[int, IceField(name="分数阈值", desc="判断分数")] = 0
            key: Annotated[str, IceField(name="键名")] = "score"
            
            def do_roam_flow(self, roam: Roam) -> bool:
                return roam.get_int(self.key, 0) >= self.score
    
    Args:
        class_name: The fully qualified class name (used for matching with config)
        name: Display name for the node
        desc: Description of the node
        order: Display order in UI
        alias: Alias names for multi-language compatibility (can be string or list)
        ice_fields: Optional dict mapping field names to IceField (legacy way)
    
    Returns:
        The decorated class (unchanged)
    """
    # Normalize alias to list
    alias_list: list[str] = []
    if alias:
        if isinstance(alias, str):
            alias_list = [alias]
        else:
            alias_list = list(alias)
    
    def decorator(cls: Type) -> Type:
        meta = LeafMeta(name=name, desc=desc, order=order, alias=alias_list, ice_fields=ice_fields)
        node_type = _detect_node_type(cls)
        extracted_ice_fields, hide_fields = _extract_fields(cls, meta)
        
        entry = LeafEntry(
            cls=cls,
            meta=meta,
            node_type=node_type,
            ice_fields=extracted_ice_fields,
            hide_fields=hide_fields,
        )
        
        # Register main class name
        _registry[class_name] = entry
        
        # Register aliases
        for a in alias_list:
            if a and a != class_name:
                _alias_registry[a] = class_name
        
        return cls
    return decorator


def register_leaf(
    class_name: str,
    cls: Type,
    meta: LeafMeta | None = None,
) -> None:
    """
    Manually register a leaf node class.
    
    Args:
        class_name: The fully qualified class name
        cls: The class to register
        meta: Optional metadata (including alias)
    """
    node_type = _detect_node_type(cls)
    ice_fields, hide_fields = _extract_fields(cls, meta)
    
    entry = LeafEntry(
        cls=cls,
        meta=meta,
        node_type=node_type,
        ice_fields=ice_fields,
        hide_fields=hide_fields,
    )
    
    _registry[class_name] = entry
    
    # Register aliases
    if meta and meta.alias:
        for a in meta.alias:
            if a and a != class_name:
                _alias_registry[a] = class_name


def resolve_class_name(conf_name: str) -> str:
    """
    Resolve a config name to the actual class name.
    If conf_name is an alias, returns the real class name.
    Otherwise returns conf_name unchanged.
    """
    return _alias_registry.get(conf_name, conf_name)


def is_registered(class_name: str) -> bool:
    """Check if a leaf class is registered (including aliases)."""
    resolved = resolve_class_name(class_name)
    return resolved in _registry


def get_leaf_nodes() -> list[LeafNodeInfo]:
    """Get information about all registered leaf nodes."""
    result = []
    for class_name, entry in _registry.items():
        info = LeafNodeInfo(
            type=entry.node_type.value,
            clazz=class_name,
            order=100,
            iceFields=entry.ice_fields,
            hideFields=entry.hide_fields,
        )
        if entry.meta:
            info.name = entry.meta.name
            info.desc = entry.meta.desc
            if entry.meta.order > 0:
                info.order = entry.meta.order
        result.append(info)
    return result


def get_alias_registry() -> dict[str, str]:
    """Get the alias registry (for debugging)."""
    return _alias_registry.copy()


def create_leaf_node(conf_name: str, conf_field: str) -> Node:
    """
    Create a leaf node from configuration.
    
    Args:
        conf_name: The class name to create (can be alias)
        conf_field: JSON configuration for the node
    
    Returns:
        A Node instance wrapping the leaf
    
    Raises:
        ValueError: If the class is not registered
    """
    # Resolve alias to real class name
    class_name = resolve_class_name(conf_name)
    
    if class_name not in _registry:
        raise ValueError(f"Leaf class not found: {conf_name} (resolved: {class_name})")
    
    entry = _registry[class_name]
    
    # Create instance
    instance = entry.cls()
    
    # Apply configuration
    if conf_field and conf_field != "{}":
        try:
            config = json.loads(conf_field)
            for key, value in config.items():
                if hasattr(instance, key):
                    setattr(instance, key, value)
        except json.JSONDecodeError:
            pass
    
    # Wrap with appropriate adapter
    return _wrap_leaf(instance, entry.node_type)


def _wrap_leaf(instance: Any, node_type: NodeType) -> Node:
    """Wrap a leaf instance with the appropriate node wrapper."""
    if node_type == NodeType.LEAF_FLOW:
        return FlowLeafNode(instance)
    elif node_type == NodeType.LEAF_RESULT:
        return ResultLeafNode(instance)
    else:
        return NoneLeafNode(instance)


class FlowLeafNode(Leaf, Node):
    """Wrapper for flow-type leaf nodes."""
    
    def __init__(self, instance: Any) -> None:
        super().__init__()
        self._instance = instance
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_leaf)
    
    def _do_leaf(self, ctx: Context) -> RunState:
        result = False
        
        # Try most specific first (Roam), then Pack, then Context
        if hasattr(self._instance, "do_roam_flow"):
            result = self._instance.do_roam_flow(ctx.pack.roam)
        elif hasattr(self._instance, "do_pack_flow"):
            result = self._instance.do_pack_flow(ctx.pack)
        elif hasattr(self._instance, "do_flow"):
            result = self._instance.do_flow(ctx)
        
        return RunState.TRUE if result else RunState.FALSE


class ResultLeafNode(Leaf, Node):
    """Wrapper for result-type leaf nodes."""
    
    def __init__(self, instance: Any) -> None:
        super().__init__()
        self._instance = instance
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_leaf)
    
    def _do_leaf(self, ctx: Context) -> RunState:
        result = False
        
        if hasattr(self._instance, "do_roam_result"):
            result = self._instance.do_roam_result(ctx.pack.roam)
        elif hasattr(self._instance, "do_pack_result"):
            result = self._instance.do_pack_result(ctx.pack)
        elif hasattr(self._instance, "do_result"):
            result = self._instance.do_result(ctx)
        
        return RunState.TRUE if result else RunState.FALSE


class NoneLeafNode(Leaf, Node):
    """Wrapper for none-type leaf nodes."""
    
    def __init__(self, instance: Any) -> None:
        super().__init__()
        self._instance = instance
    
    def process(self, ctx: Context) -> RunState:
        return process_with_base(self, ctx, self._do_leaf)
    
    def _do_leaf(self, ctx: Context) -> RunState:
        if hasattr(self._instance, "do_roam_none"):
            self._instance.do_roam_none(ctx.pack.roam)
        elif hasattr(self._instance, "do_pack_none"):
            self._instance.do_pack_none(ctx.pack)
        elif hasattr(self._instance, "do_none"):
            self._instance.do_none(ctx)
        
        return RunState.NONE


def set_leaf_base(node: Node, base: Base) -> None:
    """Set the base properties on a leaf node wrapper."""
    if isinstance(node, (FlowLeafNode, ResultLeafNode, NoneLeafNode)):
        # Copy base properties
        node.ice_node_id = base.ice_node_id
        node.ice_time_type = base.ice_time_type
        node.ice_start = base.ice_start
        node.ice_end = base.ice_end
        node.ice_node_debug = base.ice_node_debug
        node.ice_inverse = base.ice_inverse
        node.ice_forward = base.ice_forward
        node.ice_error_state = base.ice_error_state
        node.ice_log_name = base.ice_log_name
        node.ice_type = base.ice_type

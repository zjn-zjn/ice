"""AST-based scanner that extracts roam key access metadata from leaf node methods."""

from __future__ import annotations

import ast
import inspect
import logging
import textwrap
from typing import Any, Type

from ice.dto import KeyPart, RoamKeyMeta

logger = logging.getLogger(__name__)

# Roam method -> (direction, accessMode, accessMethod)
_READ_METHODS = {
    "get": ("read", "direct", "get"),
    "get_deep": ("read", "direct", "getDeep"),
    "resolve": ("read", "union", "get"),
}

_WRITE_METHODS = {
    "put": ("write", "direct", "put"),
    "put_deep": ("write", "direct", "putDeep"),
    "put_all": ("write", "direct", "put"),
}

_TARGET_METHODS = {"do_flow", "do_result", "do_none"}
_MAX_DEPTH = 3


def scan_roam_keys(cls: Type) -> list[RoamKeyMeta]:
    """Scan a leaf class for roam key accesses in its business methods."""
    try:
        return _do_scan(cls, set(), 0)
    except Exception as e:
        logger.debug("failed to scan roam keys for %s: %s", cls.__name__, e)
        return []


def _do_scan(cls: Type, visited: set[str], depth: int) -> list[RoamKeyMeta]:
    key = f"{cls.__module__}.{cls.__qualname__}"
    if key in visited or depth > _MAX_DEPTH:
        return []
    visited.add(key)

    results: list[RoamKeyMeta] = []

    for method_name in _TARGET_METHODS:
        method = getattr(cls, method_name, None)
        if method is None:
            continue
        try:
            source = inspect.getsource(method)
            source = textwrap.dedent(source)
            tree = ast.parse(source)
        except (OSError, TypeError, SyntaxError):
            continue

        # Extract actual roam parameter name from function signature (skip 'self')
        roam_param = _find_roam_param(tree)
        if not roam_param:
            continue

        # Collect assignments for local variable tracking
        assignments: dict[str, ast.expr] = {}
        _scan_node(tree, results, cls, assignments, visited, depth, roam_param)

    return _merge_directions(results)


def _find_roam_param(tree: ast.AST) -> str | None:
    """Extract the second parameter name (after self) from a method definition."""
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef):
            args = node.args.args
            # args[0] = self, args[1] = roam parameter (whatever it's named)
            if len(args) >= 2:
                return args[1].arg
            break
    return None


def _scan_node(
    node: ast.AST,
    results: list[RoamKeyMeta],
    cls: Type,
    assignments: dict[str, ast.expr],
    visited: set[str],
    depth: int,
    roam_param: str,
) -> None:
    """Recursively walk AST nodes looking for roam method calls."""
    for child in ast.walk(node):
        # Track assignments for local variable resolution
        if isinstance(child, ast.Assign):
            for target in child.targets:
                if isinstance(target, ast.Name):
                    assignments[target.id] = child.value
        elif isinstance(child, ast.AnnAssign) and isinstance(child.target, ast.Name) and child.value:
            assignments[child.target.id] = child.value

        # Look for method calls on roam
        if not isinstance(child, ast.Call):
            continue

        func = child.func
        if not isinstance(func, ast.Attribute):
            continue

        method_name = func.attr
        receiver = func.value

        # Check if receiver is the roam parameter or self.roam
        if not _is_roam_ref(receiver, roam_param, assignments):
            # Check if this is a method call that passes roam as argument
            if depth < _MAX_DEPTH:
                _check_cross_method(child, cls, results, assignments, visited, depth, roam_param)
            continue

        read_info = _READ_METHODS.get(method_name)
        write_info = _WRITE_METHODS.get(method_name)

        if read_info and child.args:
            key_arg = child.args[0]
            key_parts = _resolve_key(key_arg, cls, assignments, roam_param)
            if key_parts:
                meta = RoamKeyMeta(
                    direction=read_info[0],
                    accessMode=read_info[1],
                    accessMethod=read_info[2],
                    keyParts=key_parts,
                )
                results.append(meta)

        elif write_info and child.args:
            if method_name == "put_all":
                # put_all takes a dict - skip detailed analysis
                continue
            key_arg = child.args[0]
            key_parts = _resolve_key(key_arg, cls, assignments, roam_param)
            if key_parts:
                meta = RoamKeyMeta(
                    direction=write_info[0],
                    accessMode=write_info[1],
                    accessMethod=write_info[2],
                    keyParts=key_parts,
                )
                results.append(meta)


def _is_roam_ref(node: ast.expr, roam_param: str, assignments: dict[str, ast.expr], _seen: set[str] | None = None) -> bool:
    """Check if an AST node refers to the roam parameter."""
    if isinstance(node, ast.Name):
        if node.id == roam_param:
            return True
        # Check if it's a local variable assigned from roam
        if node.id in assignments:
            if _seen is None:
                _seen = set()
            if node.id in _seen:
                return False
            _seen.add(node.id)
            return _is_roam_ref(assignments[node.id], roam_param, assignments, _seen)
    return False


def _resolve_key(node: ast.expr, cls: Type, assignments: dict[str, ast.expr],
                  roam_param: str = "", _seen: set[str] | None = None) -> list[KeyPart] | None:
    """Resolve an AST expression to KeyPart list."""
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return [KeyPart(type="literal", value=node.value)]

    if isinstance(node, ast.Attribute) and isinstance(node.value, ast.Name) and node.value.id == "self":
        return [KeyPart(type="field", ref=node.attr)]

    if isinstance(node, ast.Name):
        # Local variable - try to resolve from assignments
        if node.id in assignments:
            if _seen is None:
                _seen = set()
            if node.id in _seen:
                return None
            _seen.add(node.id)
            return _resolve_key(assignments[node.id], cls, assignments, roam_param, _seen)
        return None

    # String concatenation with +
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        left = _resolve_key(node.left, cls, assignments, roam_param, _seen)
        right = _resolve_key(node.right, cls, assignments, roam_param, _seen)
        if left and right:
            parts = left + right
            return [KeyPart(type="composite", parts=parts)]
        return None

    # f-string
    if isinstance(node, ast.JoinedStr):
        parts: list[KeyPart] = []
        for value in node.values:
            if isinstance(value, ast.Constant) and isinstance(value.value, str):
                parts.append(KeyPart(type="literal", value=value.value))
            elif isinstance(value, ast.FormattedValue):
                sub = _resolve_key(value.value, cls, assignments, roam_param, _seen)
                if sub:
                    parts.extend(sub)
                else:
                    return None
            else:
                return None
        if parts:
            if len(parts) == 1:
                return parts
            return [KeyPart(type="composite", parts=parts)]
        return None

    # roam.get(xxx) call -> roamDerived KeyPart
    if roam_param and isinstance(node, ast.Call):
        func = getattr(node, 'func', None)
        if isinstance(func, ast.Attribute) and func.attr in _READ_METHODS:
            receiver = func.value
            if _is_roam_ref(receiver, roam_param, assignments):
                if node.args:
                    inner_key = _resolve_key(node.args[0], cls, assignments, roam_param, _seen)
                    if inner_key:
                        from_key = _key_parts_to_from_key(inner_key)
                        return [KeyPart(type="roamDerived", fromKey=from_key)]
        return None

    return None


def _key_parts_to_from_key(parts: list[KeyPart]) -> str:
    """Convert KeyPart list to a string representation for roamDerived.fromKey."""
    segs = []
    for p in parts:
        if p.type == "literal":
            segs.append(p.value or "")
        elif p.type == "field":
            segs.append(f"${{{p.ref}}}")
        elif p.type == "composite" and p.parts:
            segs.append(_key_parts_to_from_key(p.parts))
        else:
            segs.append("?")
    return "".join(segs)


def _check_cross_method(
    call: ast.Call,
    cls: Type,
    results: list[RoamKeyMeta],
    assignments: dict[str, ast.expr],
    visited: set[str],
    depth: int,
    roam_param: str,
) -> None:
    """Check if a call passes roam to another method and scan that method too."""
    # Check if any arg is the roam reference (including aliases)
    passes_roam = False
    for arg in call.args:
        if isinstance(arg, ast.Name) and _is_roam_ref(arg, roam_param, assignments):
            passes_roam = True
            break
    if not passes_roam:
        return

    # Resolve target method
    func = call.func
    if isinstance(func, ast.Attribute) and isinstance(func.value, ast.Name) and func.value.id == "self":
        target_method = getattr(cls, func.attr, None)
        if target_method:
            try:
                source = inspect.getsource(target_method)
                source = textwrap.dedent(source)
                tree = ast.parse(source)
                # Figure out roam parameter name in the target method
                for node in ast.walk(tree):
                    if isinstance(node, ast.FunctionDef):
                        # Find which parameter receives roam (skip self)
                        args_list = node.args.args
                        call_args = call.args
                        for i, arg in enumerate(call_args):
                            if isinstance(arg, ast.Name) and _is_roam_ref(arg, roam_param, assignments):
                                # This is the i-th positional arg (0-indexed from call side)
                                # In the method, skip self (index 0), so param index is i+1
                                param_idx = i + 1
                                if param_idx < len(args_list):
                                    target_roam_param = args_list[param_idx].arg
                                    sub_assignments: dict[str, ast.expr] = {}
                                    sub_results: list[RoamKeyMeta] = []
                                    _scan_node(tree, sub_results, cls, sub_assignments,
                                              visited, depth + 1, target_roam_param)
                                    results.extend(sub_results)
                        break
            except (OSError, TypeError, SyntaxError):
                pass


def _merge_directions(metas: list[RoamKeyMeta]) -> list[RoamKeyMeta]:
    """Merge metas with same key signature: read + write -> read_write."""
    if len(metas) <= 1:
        return metas

    merged: dict[str, RoamKeyMeta] = {}
    for meta in metas:
        sig = _key_signature(meta)
        existing = merged.get(sig)
        if existing is None:
            merged[sig] = meta
        elif existing.direction != meta.direction:
            existing.direction = "read_write"
    return list(merged.values())


def _key_signature(meta: RoamKeyMeta) -> str:
    parts_str = _parts_to_sig(meta.keyParts)
    return parts_str


def _parts_to_sig(parts: list[KeyPart]) -> str:
    segs = []
    for p in parts:
        if p.type == "literal":
            segs.append(f"[literal={p.value}]")
        elif p.type == "field":
            segs.append(f"[field={p.ref}]")
        elif p.type == "roamDerived":
            segs.append(f"[roamDerived={p.fromKey}]")
        elif p.type == "composite":
            segs.append(f"[composite={_parts_to_sig(p.parts)}]")
    return "".join(segs)

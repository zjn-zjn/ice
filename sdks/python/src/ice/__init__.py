"""
Ice Rule Engine Python SDK

A Python implementation of the Ice rule engine, compatible with Java and Go SDKs.
"""

from ice.context.roam import Roam
from ice.context.pack import Pack
from ice.context.context import Context
from ice.enums import RunState, NodeType, TimeType
from ice.leaf.registry import leaf, register_leaf, get_leaf_nodes, LeafMeta, IceField, IceIgnore, FieldMeta
from ice.log import Logger, set_logger
from ice.client.file_client import FileClient
from ice.client.async_file_client import AsyncFileClient
from ice.dispatcher import (
    sync_process,
    async_process,
    process_ctx,
    process_single_ctx,
    process_roam,
    process_single_roam,
    get_handler_by_id,
    get_handlers_by_scene,
)

__version__ = "1.0.0"

__all__ = [
    # Context
    "Roam",
    "Pack",
    "Context",
    # Enums
    "RunState",
    "NodeType",
    "TimeType",
    # Leaf registration
    "leaf",
    "register_leaf",
    "get_leaf_nodes",
    "IceField",
    "IceIgnore",
    "LeafMeta",
    # Logging
    "Logger",
    "set_logger",
    # Clients
    "FileClient",
    "AsyncFileClient",
    # Processing (main)
    "sync_process",
    "async_process",
    # Processing (convenience - matching Java/Go)
    "process_ctx",
    "process_single_ctx",
    "process_roam",
    "process_single_roam",
    # Handler access (matching Go)
    "get_handler_by_id",
    "get_handlers_by_scene",
]


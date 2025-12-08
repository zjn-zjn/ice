"""Data Transfer Objects for Ice SDK, compatible with Java JSON serialization."""

from dataclasses import dataclass, field
from typing import Any


@dataclass
class ConfDto:
    """Configuration DTO for a node (compatible with Java IceConfDto)."""
    id: int = 0
    sonIds: str = ""
    type: int = 0
    confName: str = ""
    confField: str = ""
    timeType: int = 0
    start: int = 0
    end: int = 0
    forwardId: int = 0
    debug: int = 0
    errorState: int = 0
    inverse: bool = False
    name: str = ""
    app: int = 0
    status: int = 0
    createAt: int = 0
    updateAt: int = 0
    # Only in ice_conf_update
    iceId: int = 0
    confId: int = 0


@dataclass
class BaseDto:
    """Base/Handler DTO (compatible with Java IceBaseDto)."""
    id: int = 0
    scenes: str = ""
    confId: int = 0
    timeType: int = 0
    start: int = 0
    end: int = 0
    debug: int = 0
    priority: int = 0
    app: int = 0
    name: str = ""
    status: int = 0
    createAt: int = 0
    updateAt: int = 0


@dataclass
class TransferDto:
    """Data transfer for configuration updates (compatible with Java IceTransferDto)."""
    version: int = 0
    insertOrUpdateConfs: list[ConfDto] = field(default_factory=list)
    deleteConfIds: list[int] = field(default_factory=list)
    insertOrUpdateBases: list[BaseDto] = field(default_factory=list)
    deleteBaseIds: list[int] = field(default_factory=list)


@dataclass
class IceFieldInfo:
    """Information about a field in a leaf node."""
    field: str = ""
    name: str = ""
    desc: str = ""
    type: str = ""
    value: Any = None
    valueNull: bool = False


@dataclass
class LeafNodeInfo:
    """Information about a leaf node class."""
    type: int = 0
    clazz: str = ""
    name: str = ""
    desc: str = ""
    order: int = 100
    iceFields: list[IceFieldInfo] = field(default_factory=list)
    hideFields: list[IceFieldInfo] = field(default_factory=list)


@dataclass
class ClientInfo:
    """Client information (compatible with Java IceClientInfo)."""
    address: str = ""
    app: int = 0
    leafNodes: list[LeafNodeInfo] = field(default_factory=list)
    lastHeartbeat: int = 0
    startTime: int = 0
    loadedVersion: int = 0


@dataclass
class AppDto:
    """Application DTO (compatible with Java IceAppDto)."""
    id: int = 0
    name: str = ""
    secret: str = ""
    createAt: int = 0
    updateAt: int = 0


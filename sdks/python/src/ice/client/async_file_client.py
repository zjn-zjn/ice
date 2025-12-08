"""Async file-based client for Ice SDK."""

from __future__ import annotations

import asyncio
import json
import os
import socket
import time
from dataclasses import asdict
from typing import TYPE_CHECKING

from ice.dto import ConfDto, BaseDto, TransferDto, ClientInfo
from ice.cache import conf_cache, handler_cache
from ice.leaf.registry import get_leaf_nodes
from ice._internal.executor import init_executor, shutdown_executor
from ice import log

if TYPE_CHECKING:
    pass


# Constants (same as file_client.py)
DIR_BASES = "bases"
DIR_CONFS = "confs"
DIR_VERSIONS = "versions"
DIR_CLIENTS = "clients"
FILE_VERSION = "version.txt"
SUFFIX_JSON = ".json"
SUFFIX_UPD = "_upd.json"

DEFAULT_POLL_INTERVAL = 5.0
DEFAULT_HEARTBEAT_INTERVAL = 30.0


class AsyncFileClient:
    """
    Async file-based client for loading ice configurations.
    
    Uses asyncio for background tasks.
    """
    
    def __init__(
        self,
        app: int,
        storage_path: str,
        parallelism: int = -1,
        poll_interval: float = DEFAULT_POLL_INTERVAL,
        heartbeat_interval: float = DEFAULT_HEARTBEAT_INTERVAL,
    ) -> None:
        """
        Initialize the async file client.
        
        Args:
            app: Application ID
            storage_path: Path to the ice-data directory
            parallelism: Thread pool size for parallel nodes (-1 = auto)
            poll_interval: Interval for polling version updates (seconds)
            heartbeat_interval: Interval for heartbeat updates (seconds)
        """
        self.app = app
        self.storage_path = storage_path
        self.parallelism = parallelism
        self.poll_interval = poll_interval if poll_interval > 0 else DEFAULT_POLL_INTERVAL
        self.heartbeat_interval = heartbeat_interval if heartbeat_interval > 0 else DEFAULT_HEARTBEAT_INTERVAL
        
        self._address = self._get_address()
        self._loaded_version = 0
        self._started = False
        self._destroyed = False
        self._poll_task: asyncio.Task | None = None
        self._heartbeat_task: asyncio.Task | None = None
    
    async def start(self) -> None:
        """Start the client and load configurations."""
        if self._started or self._destroyed:
            return
        
        # Initialize executor
        init_executor(self.parallelism)
        
        # Load initial configuration (in thread to not block)
        await asyncio.to_thread(self._load_all_config)
        
        # Register client
        await asyncio.to_thread(self._register_client)
        
        # Start background tasks
        self._poll_task = asyncio.create_task(self._version_poller())
        self._heartbeat_task = asyncio.create_task(self._heartbeat_worker())
        
        self._started = True
        log.info("async ice client started", app=self.app, version=self._loaded_version)
    
    async def destroy(self) -> None:
        """Stop the client and release resources."""
        if self._destroyed:
            return
        
        self._destroyed = True
        
        # Cancel background tasks
        if self._poll_task:
            self._poll_task.cancel()
            try:
                await self._poll_task
            except asyncio.CancelledError:
                pass
        
        if self._heartbeat_task:
            self._heartbeat_task.cancel()
            try:
                await self._heartbeat_task
            except asyncio.CancelledError:
                pass
        
        # Unregister client
        await asyncio.to_thread(self._unregister_client)
        
        # Shutdown executor
        shutdown_executor()
        
        log.info("async ice client destroyed", app=self.app)
    
    async def wait_started(self, timeout: float = 30.0) -> bool:
        """
        Wait for the client to finish starting.
        
        Args:
            timeout: Maximum time to wait in seconds
        
        Returns:
            True if started, False if timeout
        """
        start_time = time.time()
        while not self._started:
            if time.time() - start_time > timeout:
                return False
            await asyncio.sleep(0.1)
        return True
    
    @property
    def loaded_version(self) -> int:
        """Get the currently loaded configuration version."""
        return self._loaded_version
    
    def _get_address(self) -> str:
        """Get the client address (hostname:pid)."""
        try:
            hostname = socket.gethostname()
        except Exception:
            hostname = "unknown"
        return f"{hostname}:{os.getpid()}"
    
    def _get_app_path(self) -> str:
        """Get the path to the app directory."""
        return os.path.join(self.storage_path, str(self.app))
    
    async def _version_poller(self) -> None:
        """Background task that polls for version updates."""
        while not self._destroyed:
            try:
                await asyncio.to_thread(self._check_and_load_updates)
            except Exception as e:
                log.warn("error polling version", error=str(e))
            
            await asyncio.sleep(self.poll_interval)
    
    async def _heartbeat_worker(self) -> None:
        """Background task that sends heartbeats."""
        while not self._destroyed:
            try:
                await asyncio.to_thread(self._update_heartbeat)
            except Exception as e:
                log.warn("error updating heartbeat", error=str(e))
            
            await asyncio.sleep(self.heartbeat_interval)
    
    # The following methods are synchronous and run in thread pool
    
    def _load_all_config(self) -> None:
        """Load all configurations from files."""
        app_path = self._get_app_path()
        
        # Read version
        version_path = os.path.join(app_path, FILE_VERSION)
        if os.path.exists(version_path):
            try:
                with open(version_path, "r") as f:
                    self._loaded_version = int(f.read().strip())
            except Exception:
                self._loaded_version = 0
        
        # Load confs
        confs_path = os.path.join(app_path, DIR_CONFS)
        if os.path.isdir(confs_path):
            confs: list[ConfDto] = []
            for filename in os.listdir(confs_path):
                if filename.endswith(SUFFIX_JSON) and not filename.endswith(SUFFIX_UPD):
                    filepath = os.path.join(confs_path, filename)
                    try:
                        with open(filepath, "r") as f:
                            data = json.load(f)
                            confs.append(self._dict_to_conf_dto(data))
                    except Exception as e:
                        log.warn("failed to load conf", file=filepath, error=str(e))
            
            if confs:
                conf_cache.insert_or_update_confs(confs)
        
        # Load bases
        bases_path = os.path.join(app_path, DIR_BASES)
        if os.path.isdir(bases_path):
            bases: list[BaseDto] = []
            for filename in os.listdir(bases_path):
                if filename.endswith(SUFFIX_JSON):
                    filepath = os.path.join(bases_path, filename)
                    try:
                        with open(filepath, "r") as f:
                            data = json.load(f)
                            bases.append(self._dict_to_base_dto(data))
                    except Exception as e:
                        log.warn("failed to load base", file=filepath, error=str(e))
            
            if bases:
                handler_cache.insert_or_update_handlers(bases)
    
    def _check_and_load_updates(self) -> None:
        """Check for version updates and load incremental changes."""
        app_path = self._get_app_path()
        version_path = os.path.join(app_path, FILE_VERSION)
        
        if not os.path.exists(version_path):
            return
        
        try:
            with open(version_path, "r") as f:
                current_version = int(f.read().strip())
        except Exception:
            return
        
        if current_version <= self._loaded_version:
            return
        
        # Try to load incremental updates
        versions_path = os.path.join(app_path, DIR_VERSIONS)
        if not os.path.isdir(versions_path):
            return
        
        need_full_load = False
        
        for v in range(self._loaded_version + 1, current_version + 1):
            update_file = os.path.join(versions_path, f"{v}{SUFFIX_UPD}")
            if not os.path.exists(update_file):
                if v == current_version:
                    # Only the last version file is missing - normal case, wait for next poll
                    log.info("latest update file not ready, will retry", version=v)
                else:
                    # Middle version file is missing - abnormal, need full load
                    log.warn("middle update file missing, will do full load", version=v)
                    need_full_load = True
                break
            try:
                with open(update_file, "r") as f:
                    data = json.load(f)
                    transfer = self._dict_to_transfer_dto(data)
                    self._apply_transfer(transfer)
                self._loaded_version = v
                log.info("loaded incremental update", version=v)
            except Exception as e:
                log.error("failed to load incremental update", version=v, error=str(e))
                need_full_load = True
                break
        
        # Fallback to full load if incremental updates failed
        if need_full_load:
            log.info("performing full config reload")
            self._load_all_config()
            log.info("full config reload completed", version=self._loaded_version)
    
    def _apply_transfer(self, transfer: TransferDto) -> None:
        """Apply a transfer DTO to update caches."""
        if transfer.deleteConfIds:
            conf_cache.delete_confs(transfer.deleteConfIds)
        if transfer.deleteBaseIds:
            handler_cache.delete_handlers(transfer.deleteBaseIds)
        
        if transfer.insertOrUpdateConfs:
            conf_cache.insert_or_update_confs(transfer.insertOrUpdateConfs)
        if transfer.insertOrUpdateBases:
            handler_cache.insert_or_update_handlers(transfer.insertOrUpdateBases)
    
    def _register_client(self) -> None:
        """Register this client in the clients directory."""
        try:
            clients_dir = os.path.join(self.storage_path, DIR_CLIENTS, str(self.app))
            os.makedirs(clients_dir, exist_ok=True)
            
            client_info = ClientInfo(
                address=self._address,
                app=self.app,
                leafNodes=get_leaf_nodes(),
                lastHeartbeat=int(time.time() * 1000),
                startTime=int(time.time() * 1000),
                loadedVersion=self._loaded_version,
            )
            
            client_file = os.path.join(clients_dir, f"{self._address}.json")
            with open(client_file, "w") as f:
                json.dump(asdict(client_info), f)
        except Exception as e:
            log.warn("failed to register client", error=str(e))
    
    def _update_heartbeat(self) -> None:
        """Update the heartbeat timestamp."""
        try:
            clients_dir = os.path.join(self.storage_path, DIR_CLIENTS, str(self.app))
            client_file = os.path.join(clients_dir, f"{self._address}.json")
            
            if os.path.exists(client_file):
                with open(client_file, "r") as f:
                    data = json.load(f)
                
                data["lastHeartbeat"] = int(time.time() * 1000)
                data["loadedVersion"] = self._loaded_version
                
                with open(client_file, "w") as f:
                    json.dump(data, f)
        except Exception as e:
            log.warn("failed to update heartbeat", error=str(e))
    
    def _unregister_client(self) -> None:
        """Remove this client from the clients directory."""
        try:
            clients_dir = os.path.join(self.storage_path, DIR_CLIENTS, str(self.app))
            client_file = os.path.join(clients_dir, f"{self._address}.json")
            
            if os.path.exists(client_file):
                os.remove(client_file)
        except Exception as e:
            log.warn("failed to unregister client", error=str(e))
    
    def _dict_to_conf_dto(self, data: dict) -> ConfDto:
        """Convert a dictionary to ConfDto."""
        return ConfDto(
            id=data.get("id", 0),
            sonIds=data.get("sonIds", ""),
            type=data.get("type", 0),
            confName=data.get("confName", ""),
            confField=data.get("confField", ""),
            timeType=data.get("timeType", 0),
            start=data.get("start", 0),
            end=data.get("end", 0),
            forwardId=data.get("forwardId", 0),
            debug=data.get("debug", 0),
            errorState=data.get("errorState", 0),
            inverse=data.get("inverse", False),
            name=data.get("name", ""),
            app=data.get("app", 0),
            status=data.get("status", 0),
            createAt=data.get("createAt", 0),
            updateAt=data.get("updateAt", 0),
            iceId=data.get("iceId", 0),
            confId=data.get("confId", 0),
        )
    
    def _dict_to_base_dto(self, data: dict) -> BaseDto:
        """Convert a dictionary to BaseDto."""
        return BaseDto(
            id=data.get("id", 0),
            scenes=data.get("scenes", ""),
            confId=data.get("confId", 0),
            timeType=data.get("timeType", 0),
            start=data.get("start", 0),
            end=data.get("end", 0),
            debug=data.get("debug", 0),
            priority=data.get("priority", 0),
            app=data.get("app", 0),
            name=data.get("name", ""),
            status=data.get("status", 0),
            createAt=data.get("createAt", 0),
            updateAt=data.get("updateAt", 0),
        )
    
    def _dict_to_transfer_dto(self, data: dict) -> TransferDto:
        """Convert a dictionary to TransferDto."""
        confs = [self._dict_to_conf_dto(c) for c in data.get("insertOrUpdateConfs", [])]
        bases = [self._dict_to_base_dto(b) for b in data.get("insertOrUpdateBases", [])]
        
        return TransferDto(
            version=data.get("version", 0),
            insertOrUpdateConfs=confs,
            deleteConfIds=data.get("deleteConfIds", []),
            insertOrUpdateBases=bases,
            deleteBaseIds=data.get("deleteBaseIds", []),
        )


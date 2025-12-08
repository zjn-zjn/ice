"""File-based client for Ice SDK."""

from __future__ import annotations

import json
import os
import socket
import threading
import time
from dataclasses import asdict
from typing import TYPE_CHECKING

from ice.dto import ConfDto, BaseDto, TransferDto, ClientInfo
from ice.cache import conf_cache, handler_cache
from ice.leaf.registry import get_leaf_nodes
from ice._internal.executor import init_executor, shutdown_executor
from ice._internal.uuid import generate_short_id
from ice import log

if TYPE_CHECKING:
    pass


# Constants
DIR_BASES = "bases"
DIR_CONFS = "confs"
DIR_VERSIONS = "versions"
DIR_CLIENTS = "clients"
FILE_VERSION = "version.txt"
SUFFIX_JSON = ".json"
SUFFIX_UPD = "_upd.json"
SUFFIX_TMP = ".tmp"

DEFAULT_POLL_INTERVAL = 5.0  # seconds
DEFAULT_HEARTBEAT_INTERVAL = 30.0  # seconds


class FileClient:
    """
    File-based client for loading ice configurations.
    
    Reads configurations from the file system and keeps them in sync
    with updates from ice-server.
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
        Initialize the file client.
        
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
        self._stop_event = threading.Event()
        self._poll_thread: threading.Thread | None = None
        self._heartbeat_thread: threading.Thread | None = None
        self._start_lock = threading.Lock()
    
    def start(self) -> None:
        """Start the client and load configurations."""
        with self._start_lock:
            if self._started or self._destroyed:
                return
            
            # Initialize executor
            init_executor(self.parallelism)
            
            # Load initial configuration
            self._load_all_config()
            
            # Register client
            self._register_client()
            
            # Start background threads
            self._stop_event.clear()
            
            self._poll_thread = threading.Thread(
                target=self._version_poller,
                name="ice-version-poller",
                daemon=True,
            )
            self._poll_thread.start()
            
            self._heartbeat_thread = threading.Thread(
                target=self._heartbeat_worker,
                name="ice-heartbeat",
                daemon=True,
            )
            self._heartbeat_thread.start()
            
            self._started = True
            log.info("ice client started", app=self.app, version=self._loaded_version)
    
    def destroy(self) -> None:
        """Stop the client and release resources."""
        with self._start_lock:
            if self._destroyed:
                return
            
            self._destroyed = True
            self._stop_event.set()
            
            # Wait for threads to stop
            if self._poll_thread and self._poll_thread.is_alive():
                self._poll_thread.join(timeout=2.0)
            if self._heartbeat_thread and self._heartbeat_thread.is_alive():
                self._heartbeat_thread.join(timeout=2.0)
            
            # Unregister client
            self._unregister_client()
            
            # Shutdown executor
            shutdown_executor()
            
            log.info("ice client destroyed", app=self.app)
    
    def wait_started(self, timeout: float = 30.0) -> bool:
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
            time.sleep(0.1)
        return True
    
    @property
    def loaded_version(self) -> int:
        """Get the currently loaded configuration version."""
        return self._loaded_version
    
    def _get_address(self) -> str:
        """Get the client address (hostname/app/uuid) - same format as Java/Go."""
        try:
            hostname = socket.gethostname()
        except Exception:
            hostname = "unknown"
        return f"{hostname}/{self.app}/{generate_short_id()}"
    
    def _get_app_path(self) -> str:
        """Get the path to the app directory."""
        return os.path.join(self.storage_path, str(self.app))
    
    def _version_poller(self) -> None:
        """Background thread that polls for version updates."""
        while not self._stop_event.is_set():
            try:
                self._check_and_load_updates()
            except Exception as e:
                log.warn("error polling version", error=str(e))
            
            self._stop_event.wait(self.poll_interval)
    
    def _heartbeat_worker(self) -> None:
        """Background thread that sends heartbeats."""
        while not self._stop_event.is_set():
            try:
                self._update_heartbeat()
            except Exception as e:
                log.warn("error updating heartbeat", error=str(e))
            
            self._stop_event.wait(self.heartbeat_interval)
    
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
        # Delete first
        if transfer.deleteConfIds:
            conf_cache.delete_confs(transfer.deleteConfIds)
        if transfer.deleteBaseIds:
            handler_cache.delete_handlers(transfer.deleteBaseIds)
        
        # Then insert/update
        if transfer.insertOrUpdateConfs:
            conf_cache.insert_or_update_confs(transfer.insertOrUpdateConfs)
        if transfer.insertOrUpdateBases:
            handler_cache.insert_or_update_handlers(transfer.insertOrUpdateBases)
    
    def _get_client_file_path(self) -> str:
        """Get the client file path with safe filename (replace / and : with _)."""
        safe_address = self._address.replace(":", "_").replace("/", "_")
        clients_dir = os.path.join(self.storage_path, DIR_CLIENTS, str(self.app))
        return os.path.join(clients_dir, f"{safe_address}.json")
    
    def _register_client(self) -> None:
        """Register this client in the clients directory."""
        try:
            clients_dir = os.path.join(self.storage_path, DIR_CLIENTS, str(self.app))
            os.makedirs(clients_dir, exist_ok=True)
            
            leaf_nodes = get_leaf_nodes()
            client_info = ClientInfo(
                address=self._address,
                app=self.app,
                leafNodes=leaf_nodes,
                lastHeartbeat=int(time.time() * 1000),
                startTime=int(time.time() * 1000),
                loadedVersion=self._loaded_version,
            )
            
            client_file = self._get_client_file_path()
            with open(client_file, "w") as f:
                json.dump(asdict(client_info), f)
            
            # 每次注册都覆盖 _latest.json，server 从这里获取最新的叶子节点结构
            if leaf_nodes:
                latest_file = os.path.join(clients_dir, "_latest.json")
                tmp_file = latest_file + SUFFIX_TMP
                with open(tmp_file, "w") as f:
                    json.dump(asdict(client_info), f)
                os.replace(tmp_file, latest_file)
        except Exception as e:
            log.warn("failed to register client", error=str(e))
    
    def _update_heartbeat(self) -> None:
        """Update the heartbeat timestamp."""
        try:
            client_file = self._get_client_file_path()
            
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
            client_file = self._get_client_file_path()
            
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


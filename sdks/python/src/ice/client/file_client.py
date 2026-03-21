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
from ice._internal.uuid import generate_alphanum_id
from ice import log
from ice.context.roam import Roam

if TYPE_CHECKING:
    pass


# Constants
DIR_BASES = "bases"
DIR_CONFS = "confs"
DIR_VERSIONS = "versions"
DIR_CLIENTS = "clients"
DIR_LANE = "lane"
DIR_MOCK = "mock"
FILE_VERSION = "version.txt"
SUFFIX_JSON = ".json"
SUFFIX_UPD = "_upd.json"
SUFFIX_TMP = ".tmp"

DEFAULT_POLL_INTERVAL = 2.0  # seconds
DEFAULT_HEARTBEAT_INTERVAL = 10.0  # seconds


def _get_host_ip() -> str:
    """Get a non-loopback IPv4 address for machine identification, fallback to hostname."""
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if not ip.startswith("127."):
                return ip
    except Exception:
        pass
    try:
        return socket.gethostname()
    except Exception:
        return "unknown"


def _find_node_by_id(node, node_id: int):
    """Walk the tree to find a node by its ID."""
    if node is None:
        return None
    if node.get_node_id() == node_id:
        return node
    # Check forward
    from ice.node.base import Base
    if isinstance(node, Base) and node.ice_forward is not None:
        found = _find_node_by_id(node.ice_forward, node_id)
        if found is not None:
            return found
    # Check children (relation nodes)
    from ice.node.relation import Relation
    if isinstance(node, Relation):
        for child in node.children:
            found = _find_node_by_id(child, node_id)
            if found is not None:
                return found
    return None


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
        lane: str = "",
    ) -> None:
        """
        Initialize the file client.
        
        Args:
            app: Application ID
            storage_path: Path to the ice-data directory
            parallelism: Thread pool size for parallel nodes (-1 = auto)
            poll_interval: Interval for polling version updates (seconds)
            heartbeat_interval: Interval for heartbeat updates (seconds)
            lane: Swimlane name (empty string means main trunk)
        """
        self.app = app
        self.storage_path = storage_path
        self.parallelism = parallelism
        self.poll_interval = poll_interval if poll_interval > 0 else DEFAULT_POLL_INTERVAL
        self.heartbeat_interval = heartbeat_interval if heartbeat_interval > 0 else DEFAULT_HEARTBEAT_INTERVAL
        self.lane = lane.strip() if lane and lane.strip() else None
        
        self._address = self._get_address()
        self._start_time_ms = int(time.time() * 1000)
        self._loaded_version = 0
        self._started = False
        self._destroyed = False
        self._stop_event = threading.Event()
        self._poll_thread: threading.Thread | None = None
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
            
            self._started = True
            log.info("ice client started", app=self.app, lane=self.lane, version=self._loaded_version)
    
    def destroy(self) -> None:
        """Stop the client and release resources."""
        with self._start_lock:
            if self._destroyed:
                return
            
            self._destroyed = True
            self._stop_event.set()
            
            # Wait for thread to stop
            if self._poll_thread and self._poll_thread.is_alive():
                self._poll_thread.join(timeout=2.0)
            
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
        """Get the client address (ip_id) - same format as Java/Go."""
        return f"{_get_host_ip()}_{generate_alphanum_id(5)}"
    
    def _get_app_path(self) -> str:
        """Get the path to the app directory."""
        return os.path.join(self.storage_path, str(self.app))
    
    def _version_poller(self) -> None:
        """Background thread that polls for version updates and heartbeats."""
        heartbeat_ticks = max(1, int(self.heartbeat_interval / self.poll_interval))
        tick_count = 0
        while not self._stop_event.is_set():
            try:
                self._check_and_load_updates()
            except Exception as e:
                log.warn("error polling version", error=str(e))
            try:
                self._check_mocks()
            except Exception as e:
                log.warn("error checking mocks", error=str(e))
            tick_count += 1
            if tick_count >= heartbeat_ticks:
                tick_count = 0
                try:
                    self._update_heartbeat()
                except Exception as e:
                    log.warn("error updating heartbeat", error=str(e))

            self._stop_event.wait(self.poll_interval)
    
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
        
        # Load bases (recursively walk directories to support folder structure)
        bases_path = os.path.join(app_path, DIR_BASES)
        if os.path.isdir(bases_path):
            bases: list[BaseDto] = []
            for dirpath, _, filenames in os.walk(bases_path):
                for filename in filenames:
                    if filename.endswith(SUFFIX_JSON):
                        fpath = os.path.join(dirpath, filename)
                        try:
                            with open(fpath, "r") as f:
                                data = json.load(f)
                                bases.append(self._dict_to_base_dto(data))
                        except Exception as e:
                            log.warn("failed to load base", file=fpath, error=str(e))
            
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
    
    def _get_clients_dir(self) -> str:
        """Get the clients directory, with lane support."""
        if self.lane:
            return os.path.join(self.storage_path, DIR_CLIENTS, str(self.app), DIR_LANE, self.lane)
        return os.path.join(self.storage_path, DIR_CLIENTS, str(self.app))

    def _safe_address(self) -> str:
        return self._address.replace(":", "_").replace("/", "_")

    def _meta_file_path(self) -> str:
        return os.path.join(self._get_clients_dir(), f"m_{self._safe_address()}.json")

    def _beat_file_path(self) -> str:
        return os.path.join(self._get_clients_dir(), f"b_{self._safe_address()}.json")

    def _register_client(self) -> None:
        """Register this client in the clients directory."""
        try:
            clients_dir = self._get_clients_dir()
            os.makedirs(clients_dir, exist_ok=True)

            leaf_nodes = get_leaf_nodes()
            client_info = ClientInfo(
                address=self._address,
                app=self.app,
                lane=self.lane,
                leafNodes=leaf_nodes,
                lastHeartbeat=int(time.time() * 1000),
                startTime=self._start_time_ms,
                loadedVersion=self._loaded_version,
            )

            # Write m_{addr}.json (full info with leafNodes)
            self._write_json_file(self._meta_file_path(), asdict(client_info))
            # Write b_{addr}.json (heartbeat)
            self._write_beat_file()
            # Overwrite _latest.json on registration
            if leaf_nodes:
                self._write_json_file(
                    os.path.join(clients_dir, "_latest.json"),
                    asdict(client_info),
                )
        except Exception as e:
            log.warn("failed to register client", error=str(e))

    def _write_json_file(self, path: str, data: dict) -> None:
        """Atomic write JSON file via temp + rename."""
        tmp_path = path + SUFFIX_TMP
        with open(tmp_path, "w") as f:
            json.dump(data, f)
        os.replace(tmp_path, path)

    def _write_beat_file(self) -> None:
        """Write b_{addr}.json (~50 bytes)."""
        hb = {"lastHeartbeat": int(time.time() * 1000), "loadedVersion": self._loaded_version}
        self._write_json_file(self._beat_file_path(), hb)

    def _update_heartbeat(self) -> None:
        """Update the heartbeat file."""
        try:
            if not os.path.exists(self._meta_file_path()):
                self._register_client()
                return
            self._write_beat_file()
        except Exception as e:
            log.warn("failed to update heartbeat", error=str(e))

    def _unregister_client(self) -> None:
        """Remove mock dir, then m_, then b_ last."""
        try:
            import shutil
            mock_dir = self._get_mock_dir()
            if os.path.isdir(mock_dir):
                shutil.rmtree(mock_dir, ignore_errors=True)
            for path in (self._meta_file_path(), self._beat_file_path()):
                if os.path.exists(path):
                    os.remove(path)
        except Exception as e:
            log.warn("failed to unregister client", error=str(e))
    
    def _get_mock_dir(self) -> str:
        """Get the mock directory for this client."""
        safe_addr = self._address.replace(":", "_").replace("/", "_")
        return os.path.join(self.storage_path, DIR_MOCK, str(self.app), safe_addr)

    def _check_mocks(self) -> None:
        """Check for and execute mock requests."""
        mock_dir = self._get_mock_dir()
        if not os.path.isdir(mock_dir):
            return

        for filename in os.listdir(mock_dir):
            if not filename.endswith(SUFFIX_JSON) or filename.endswith("_result.json"):
                continue

            filepath = os.path.join(mock_dir, filename)
            try:
                with open(filepath, "r") as f:
                    req = json.load(f)

                # Delete request file first to prevent re-execution on crash
                os.remove(filepath)

                result = self._execute_mock(req)

                # Write result file
                result_path = os.path.join(mock_dir, req["mockId"] + "_result" + SUFFIX_JSON)
                tmp_path = result_path + SUFFIX_TMP
                with open(tmp_path, "w") as f:
                    json.dump(result, f)
                os.replace(tmp_path, result_path)

                log.info("mock executed", mockId=req["mockId"], success=result["success"])
            except Exception as e:
                log.warn("failed to process mock file", file=filename, error=str(e))

    def _execute_mock(self, req: dict) -> dict:
        """Execute a mock request and return the result dict."""
        result = {
            "mockId": req["mockId"],
            "executeAt": int(time.time() * 1000),
        }

        try:
            from ice.handler.handler import Handler
            from ice.node.relation import Relation

            roam = Roam.create()
            meta = roam.get_meta()
            meta.id = req.get("iceId", 0)
            meta.nid = req.get("confId", 0)
            meta.scene = req.get("scene", "")
            meta.debug = req.get("debug", 0)
            ts = req.get("ts", 0)
            if ts > 0:
                meta.ts = ts

            # Put user roam data
            user_roam = req.get("roam")
            if user_roam:
                roam.put_all(user_roam)

            # Dispatch using cache directly (same logic as Go executeMock)
            handled = False
            if meta.id > 0 and meta.nid > 0:
                # Both iceId and confId: get handler by iceId, find confId subtree
                h = handler_cache.get_handler_by_id(meta.id)
                if h is not None and h.root is not None:
                    if meta.debug == 0:
                        meta.debug = h.debug
                    subtree = _find_node_by_id(h.root, meta.nid)
                    if subtree is not None:
                        sub = Handler()
                        sub.debug = meta.debug
                        sub.root = subtree
                        sub.conf_id = meta.nid
                        sub.handle_with_node_id(roam)
                        handled = True
            elif meta.id > 0:
                h = handler_cache.get_handler_by_id(meta.id)
                if h is not None:
                    if meta.debug == 0:
                        meta.debug = h.debug
                    h.handle(roam)
                    handled = True
            elif meta.scene:
                handlers_map = handler_cache.get_handlers_by_scene(meta.scene)
                if handlers_map:
                    for h in handlers_map.values():
                        if meta.debug == 0:
                            meta.debug = h.debug
                        meta.id = h.ice_id
                        h.handle(roam)
                        handled = True
                        break  # mock only handles first matching handler
            elif meta.nid > 0:
                root = conf_cache.get_conf(meta.nid)
                if root is not None:
                    sub = Handler()
                    sub.debug = meta.debug
                    sub.root = root
                    sub.conf_id = meta.nid
                    sub.handle_with_node_id(roam)
                    handled = True

            if not handled:
                result["success"] = False
                result["error"] = "no matching handler found"
                result["trace"] = roam.get_ice_trace()
                result["ts"] = roam.get_ice_ts()
                return result

            result["success"] = True
            result["trace"] = roam.get_ice_trace()
            result["ts"] = roam.get_ice_ts()
            roam_data = roam.to_dict()
            roam_data.pop("_ice", None)
            result["roam"] = roam_data

            process_info = roam.get_meta().get_process_info()
            if process_info:
                result["process"] = process_info
        except Exception as e:
            result["success"] = False
            result["error"] = str(e)
            result["trace"] = roam.get_ice_trace()
            result["ts"] = roam.get_ice_ts()

        return result

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


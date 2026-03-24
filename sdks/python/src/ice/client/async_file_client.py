"""Async file-based client for Ice SDK."""

from __future__ import annotations

import asyncio
import json
import os
import time
from dataclasses import asdict
from typing import TYPE_CHECKING

from ice.dto import ConfDto, BaseDto, TransferDto, ClientInfo
from ice.cache import conf_cache, handler_cache
from ice.leaf.registry import get_leaf_nodes
from ice._internal.executor import init_executor, shutdown_executor
from ice import log
from ice.context.roam import Roam

if TYPE_CHECKING:
    pass


# Constants (same as file_client.py)
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

DEFAULT_POLL_INTERVAL = 2.0
DEFAULT_HEARTBEAT_INTERVAL = 10.0


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
        lane: str = "",
    ) -> None:
        """
        Initialize the async file client.

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
        self._poll_task: asyncio.Task | None = None

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

        # Start background task (heartbeat is merged into poller via counter)
        self._poll_task = asyncio.create_task(self._version_poller())

        self._started = True
        log.info("client started", extra={"app": self.app, "lane": self.lane, "version": self._loaded_version})

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

        # Unregister client
        await asyncio.to_thread(self._unregister_client)

        # Shutdown executor
        shutdown_executor()

        log.info("client stopped", extra={"app": self.app})

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
        """Get the client address (ip_id)."""
        from ice.client.file_client import _get_host_ip
        from ice._internal.uuid import generate_alphanum_id
        return f"{_get_host_ip()}_{generate_alphanum_id(5)}"

    def _get_app_path(self) -> str:
        """Get the path to the app directory."""
        return os.path.join(self.storage_path, str(self.app))

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

    async def _version_poller(self) -> None:
        """Background task that polls for version updates and heartbeats."""
        heartbeat_ticks = max(1, int(self.heartbeat_interval / self.poll_interval))
        tick_count = 0
        while not self._destroyed:
            try:
                await asyncio.to_thread(self._check_and_load_updates)
            except Exception as e:
                log.warn("version poll failed", extra={"error": str(e)})
            try:
                await asyncio.to_thread(self._check_mocks)
            except Exception as e:
                log.warn("mock check failed", extra={"error": str(e)})
            tick_count += 1
            if tick_count >= heartbeat_ticks:
                tick_count = 0
                try:
                    await asyncio.to_thread(self._update_heartbeat)
                except Exception as e:
                    log.warn("heartbeat update failed", extra={"error": str(e)})

            await asyncio.sleep(self.poll_interval)

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
                        log.warn("conf file load failed", extra={"file": filepath, "error": str(e)})

            if confs:
                conf_cache.insert_or_update_confs(confs)

        # Load bases (recursively walk directories to support folder structure)
        bases_path = os.path.join(app_path, DIR_BASES)
        if os.path.isdir(bases_path):
            bases: list[BaseDto] = []
            for dirpath, _, filenames in os.walk(bases_path):
                for filename in filenames:
                    if filename.endswith(SUFFIX_JSON):
                        filepath = os.path.join(dirpath, filename)
                        try:
                            with open(filepath, "r") as f:
                                data = json.load(f)
                                bases.append(self._dict_to_base_dto(data))
                        except Exception as e:
                            log.warn("base file load failed", extra={"file": filepath, "error": str(e)})

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
                    log.info("update file not ready, retrying", extra={"version": v})
                else:
                    # Middle version file is missing - abnormal, need full load
                    log.warn("incremental file missing, falling back to full load", extra={"version": v})
                    need_full_load = True
                break
            try:
                with open(update_file, "r") as f:
                    data = json.load(f)
                    transfer = self._dict_to_transfer_dto(data)
                    self._apply_transfer(transfer)
                self._loaded_version = v
                log.info("incremental update loaded", extra={"version": v})
            except Exception as e:
                log.error("incremental update load failed", extra={"version": v, "error": str(e)})
                need_full_load = True
                break

        # Fallback to full load if incremental updates failed
        if need_full_load:
            log.info("full reload started")
            self._load_all_config()
            log.info("full reload completed", extra={"version": self._loaded_version})

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
            log.warn("client register failed", extra={"error": str(e)})

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
            log.warn("heartbeat update failed", extra={"error": str(e)})

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
            log.warn("client unregister failed", extra={"error": str(e)})

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

                log.info("mock executed", extra={"mockId": req["mockId"], "success": result["success"]})
            except Exception as e:
                log.warn("mock file process failed", extra={"file": filename, "error": str(e)})

    def _execute_mock(self, req: dict) -> dict:
        """Execute a mock request and return the result dict."""
        from ice.client.file_client import _find_node_by_id

        result = {
            "mockId": req["mockId"],
            "executeAt": int(time.time() * 1000),
        }

        try:
            from ice.handler.handler import Handler

            roam = Roam.create()
            roam.set_id(req.get("iceId", 0))
            roam.set_nid(req.get("confId", 0))
            roam.set_scene(req.get("scene", ""))
            roam.set_debug(req.get("debug", 0))
            ts = req.get("ts", 0)
            if ts > 0:
                roam.set_ts(ts)

            # Put user roam data
            user_roam = req.get("roam")
            if user_roam:
                roam.put_all(user_roam)

            # Dispatch using cache directly (same logic as Go executeMock)
            handled = False
            if roam.get_id() > 0 and roam.get_nid() > 0:
                # Both iceId and confId: get handler by iceId, find confId subtree
                h = handler_cache.get_handler_by_id(roam.get_id())
                if h is not None and h.root is not None:
                    if roam.get_debug() == 0:
                        roam.set_debug(h.debug)
                    subtree = _find_node_by_id(h.root, roam.get_nid())
                    if subtree is not None:
                        sub = Handler()
                        sub.debug = roam.get_debug()
                        sub.root = subtree
                        sub.conf_id = roam.get_nid()
                        sub.handle_with_node_id(roam)
                        handled = True
            elif roam.get_id() > 0:
                h = handler_cache.get_handler_by_id(roam.get_id())
                if h is not None:
                    if roam.get_debug() == 0:
                        roam.set_debug(h.debug)
                    h.handle(roam)
                    handled = True
            elif roam.get_scene():
                handlers_map = handler_cache.get_handlers_by_scene(roam.get_scene())
                if handlers_map:
                    for h in handlers_map.values():
                        if roam.get_debug() == 0:
                            roam.set_debug(h.debug)
                        roam.set_id(h.ice_id)
                        h.handle(roam)
                        handled = True
                        break  # mock only handles first matching handler
            elif roam.get_nid() > 0:
                from ice.cache import conf_cache
                root = conf_cache.get_conf(roam.get_nid())
                if root is not None:
                    sub = Handler()
                    sub.debug = roam.get_debug()
                    sub.root = root
                    sub.conf_id = roam.get_nid()
                    sub.handle_with_node_id(roam)
                    handled = True

            if not handled:
                result["success"] = False
                result["error"] = "no matching handler found"
                result["trace"] = roam.get_trace()
                result["ts"] = roam.get_ts()
                return result

            result["success"] = True
            result["trace"] = roam.get_trace()
            result["ts"] = roam.get_ts()
            roam_data = roam.to_dict()
            roam_data.pop("_ice", None)
            result["roam"] = roam_data

            process_info = roam.get_process_info()
            if process_info:
                result["process"] = process_info
        except Exception as e:
            result["success"] = False
            result["error"] = str(e)
            result["trace"] = roam.get_trace()
            result["ts"] = roam.get_ts()

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

package com.ice.core.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ice.common.constant.Constant;
import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceClientInfo;
import com.ice.common.dto.IceConfDto;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.dto.MockRequest;
import com.ice.common.dto.MockResult;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.LeafNodeInfo;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import com.ice.core.handler.IceHandler;
import com.ice.core.annotation.IceField;
import com.ice.core.context.IceMeta;
import com.ice.core.context.IceRoam;
import com.ice.core.annotation.IceIgnore;
import com.ice.core.annotation.IceNode;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.scan.RoamKeyScanner;
import com.ice.core.utils.IceLinkedList;
import com.ice.core.utils.IceAddressUtils;
import com.ice.core.utils.IceBeanUtils;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * File system based Ice client.
 * Replaces the original IceNioClient.
 *
 * @author waitmoon
 */
@Slf4j
public final class IceFileClient {

    private final int app;
    private final String storagePath;
    private final int parallelism;
    private final int pollIntervalSeconds;
    private final int heartbeatIntervalSeconds;
    private final String iceAddress;
    private final String lane;

    private List<LeafNodeInfo> leafNodes;
    private long startTimeMs;
    private volatile long loadedVersion = 0;
    private volatile boolean started = false;
    private volatile boolean destroy = false;

    private final AtomicBoolean startedLock = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private int heartbeatTickCounter = 0;

    // Default configuration
    private static final int DEFAULT_PARALLELISM = -1;

    public IceFileClient(int app, String storagePath, int parallelism, Set<String> scanPackages,
                         int pollIntervalSeconds, int heartbeatIntervalSeconds, String lane) throws IOException {
        this.app = app;
        this.storagePath = storagePath;
        this.parallelism = parallelism;
        this.pollIntervalSeconds = pollIntervalSeconds > 0 ? pollIntervalSeconds : IceStorageConstants.DEFAULT_POLL_INTERVAL_SECONDS;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds > 0 ? heartbeatIntervalSeconds : IceStorageConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS;
        this.iceAddress = IceAddressUtils.getAddress(app);
        this.lane = lane != null && !lane.trim().isEmpty() ? lane.trim() : null;

        scanLeafNodes(scanPackages);
        prepare();
    }

    public IceFileClient(int app, String storagePath, int parallelism, Set<String> scanPackages,
                         int pollIntervalSeconds, int heartbeatIntervalSeconds) throws IOException {
        this(app, storagePath, parallelism, scanPackages, pollIntervalSeconds, heartbeatIntervalSeconds, null);
    }

    public IceFileClient(int app, String storagePath, Set<String> scanPackages) throws IOException {
        this(app, storagePath, DEFAULT_PARALLELISM, scanPackages,
                IceStorageConstants.DEFAULT_POLL_INTERVAL_SECONDS,
                IceStorageConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS, null);
    }

    public IceFileClient(int app, String storagePath, String scan) throws IOException {
        this(app, storagePath, new HashSet<>(Arrays.asList(scan.split(Constant.REGEX_COMMA))));
    }

    public IceFileClient(int app, String storagePath) throws IOException {
        this(app, storagePath, Collections.emptySet());
    }

    public static IceFileClient newWithLane(int app, String storagePath, String scan, String lane) throws IOException {
        return new IceFileClient(app, storagePath, DEFAULT_PARALLELISM,
                new HashSet<>(Arrays.asList(scan.split(Constant.REGEX_COMMA))),
                IceStorageConstants.DEFAULT_POLL_INTERVAL_SECONDS,
                IceStorageConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS, lane);
    }

    private void prepare() {
        if (parallelism <= 0) {
            IceExecutor.setExecutor(new ForkJoinPool());
        } else {
            IceExecutor.setExecutor(new ForkJoinPool(parallelism));
        }
        scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "ice-file-client-" + app);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the client.
     * 1. Load configuration from file system
     * 2. Register client information
     * 3. Start version polling and heartbeat tasks
     */
    public void start() throws Exception {
        destroy = false;
        startTimeMs = System.currentTimeMillis();
        long startTime = startTimeMs;

        // Ensure directories exist
        ensureDirectories();

        // Load initial configuration
        loadInitialConfig();

        // Register client information
        registerClient();

        // Start version polling (heartbeat is merged into poller via counter)
        startVersionPoller();

        started = true;
        startedLock.set(true);
        log.info("ice file client init app:{} address:{} lane:{} success:{}ms storagePath:{}",
                app, iceAddress, lane, System.currentTimeMillis() - startTime, storagePath);
    }

    /**
     * Wait for startup to complete.
     */
    public void waitStarted() {
        while (!started && !destroy) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Destroy the client.
     */
    public void destroy() {
        destroy = true;
        started = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Clean up client file
        unregisterClient();
        log.info("ice file client destroyed app:{} address:{}", app, iceAddress);
    }

    public boolean isDestroy() {
        return destroy;
    }

    private void ensureDirectories() throws IOException {
        Path appPath = Paths.get(storagePath, String.valueOf(app));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_BASES));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_CONFS));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_VERSIONS));

        Files.createDirectories(getClientsDir());
    }

    private Path getClientsDir() {
        if (lane != null) {
            return Paths.get(storagePath, IceStorageConstants.DIR_CLIENTS, String.valueOf(app),
                    IceStorageConstants.DIR_LANE, lane);
        }
        return Paths.get(storagePath, IceStorageConstants.DIR_CLIENTS, String.valueOf(app));
    }

    /**
     * Load initial configuration.
     */
    private void loadInitialConfig() throws IOException {
        IceTransferDto initData = loadAllConfig();
        if (initData != null) {
            List<String> errors = IceUpdate.update(initData);
            if (!errors.isEmpty()) {
                log.warn("ice init config has errors: {}", errors);
            }
            loadedVersion = initData.getVersion();
        }
        log.info("ice file client loaded initial config, version:{}", loadedVersion);
    }

    /**
     * Load all configuration.
     */
    private IceTransferDto loadAllConfig() throws IOException {
        IceTransferDto dto = new IceTransferDto();
        Path appPath = Paths.get(storagePath, String.valueOf(app));

        // Read version number
        Path versionPath = appPath.resolve(IceStorageConstants.FILE_VERSION);
        if (Files.exists(versionPath)) {
            String versionStr = new String(Files.readAllBytes(versionPath), StandardCharsets.UTF_8).trim();
            dto.setVersion(Long.parseLong(versionStr));
        } else {
            dto.setVersion(0);
        }

        // Read all bases (recursively walk directories to support folder structure)
        Path basesPath = appPath.resolve(IceStorageConstants.DIR_BASES);
        List<IceBaseDto> bases = new ArrayList<>();
        if (Files.exists(basesPath)) {
            Files.walk(basesPath)
                    .filter(p -> !Files.isDirectory(p) && p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .forEach(p -> {
                        try {
                            String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            IceBaseDto base = JacksonUtils.readJson(content, IceBaseDto.class);
                            if (base != null && base.getStatus() != null
                                    && base.getStatus() != IceStorageConstants.STATUS_DELETED) {
                                bases.add(base);
                            }
                        } catch (Exception e) {
                            log.error("failed to read base file: {}", p, e);
                        }
                    });
        }
        dto.setInsertOrUpdateBases(bases);

        // Read all confs
        Path confsPath = appPath.resolve(IceStorageConstants.DIR_CONFS);
        List<IceConfDto> confs = new ArrayList<>();
        if (Files.exists(confsPath)) {
            Files.list(confsPath)
                    .filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .forEach(p -> {
                        try {
                            String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                            IceConfDto conf = JacksonUtils.readJson(content, IceConfDto.class);
                            if (conf != null && conf.getStatus() != null
                                    && conf.getStatus() != IceStorageConstants.STATUS_DELETED) {
                                confs.add(conf);
                            }
                        } catch (Exception e) {
                            log.error("failed to read conf file: {}", p, e);
                        }
                    });
        }
        dto.setInsertOrUpdateConfs(confs);

        return dto;
    }

    private String safeAddress() {
        return iceAddress.replace(":", "_").replace("/", "_");
    }

    private Path metaFilePath() {
        return getClientsDir().resolve("m_" + safeAddress() + IceStorageConstants.SUFFIX_JSON);
    }

    private Path beatFilePath() {
        return getClientsDir().resolve("b_" + safeAddress() + IceStorageConstants.SUFFIX_JSON);
    }

    /**
     * Register client information.
     */
    private void registerClient() throws IOException {
        Files.createDirectories(getClientsDir());

        IceClientInfo clientInfo = new IceClientInfo();
        clientInfo.setAddress(iceAddress);
        clientInfo.setApp(app);
        clientInfo.setLane(lane);
        clientInfo.setLeafNodes(leafNodes);
        clientInfo.setLastHeartbeat(System.currentTimeMillis());
        clientInfo.setStartTime(startTimeMs);
        clientInfo.setLoadedVersion(loadedVersion);

        // Write m_{addr}.json (full info with leafNodes)
        writeJsonFile(metaFilePath(), JacksonUtils.toJsonString(clientInfo));
        // Write b_{addr}.json (heartbeat)
        writeBeatFile();
        // Overwrite _latest.json on registration
        if (leafNodes != null && !leafNodes.isEmpty()) {
            writeJsonFile(getClientsDir().resolve("_latest.json"), JacksonUtils.toJsonString(clientInfo));
        }
        log.info("ice client registered: {}", iceAddress);
    }

    /**
     * Unregister client.
     */
    private void unregisterClient() {
        try {
            // Delete mock directory first, then m_, then b_ last
            deleteDirectory(getMockDir());
            Files.deleteIfExists(metaFilePath());
            Files.deleteIfExists(beatFilePath());
            log.info("ice client unregistered: {}", iceAddress);
        } catch (IOException e) {
            log.error("failed to unregister client", e);
        }
    }

    private void deleteDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            entries.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
        }
    }

    private void writeJsonFile(Path path, String json) throws IOException {
        Path tmpPath = Paths.get(path.toString() + IceStorageConstants.SUFFIX_TMP);
        Files.write(tmpPath, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpPath, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeBeatFile() throws IOException {
        String json = String.format("{\"lastHeartbeat\":%d,\"loadedVersion\":%d}",
                System.currentTimeMillis(), loadedVersion);
        writeJsonFile(beatFilePath(), json);
    }

    /**
     * Start version polling.
     */
    private void startVersionPoller() {
        int heartbeatTicks = Math.max(1, heartbeatIntervalSeconds / pollIntervalSeconds);
        scheduler.scheduleWithFixedDelay(() -> {
            if (destroy) return;
            try {
                checkAndUpdateVersion();
            } catch (Exception e) {
                log.error("version poll error", e);
            }
            try {
                checkMocks();
            } catch (Exception e) {
                log.error("mock check error", e);
            }
            heartbeatTickCounter++;
            if (heartbeatTickCounter >= heartbeatTicks) {
                heartbeatTickCounter = 0;
                try {
                    updateHeartbeat();
                } catch (Exception e) {
                    log.error("heartbeat error", e);
                }
            }
        }, pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Check and update version.
     */
    private void checkAndUpdateVersion() throws IOException {
        Path versionPath = Paths.get(storagePath, String.valueOf(app), IceStorageConstants.FILE_VERSION);
        if (!Files.exists(versionPath)) {
            return;
        }

        String versionStr = new String(Files.readAllBytes(versionPath), StandardCharsets.UTF_8).trim();
        long currentVersion = Long.parseLong(versionStr);

        if (currentVersion > loadedVersion) {
            log.info("detected version change: {} -> {}", loadedVersion, currentVersion);
            loadIncrementalUpdates(currentVersion);
        }
    }

    /**
     * Load incremental updates.
     */
    private void loadIncrementalUpdates(long targetVersion) throws IOException {
        Path versionsPath = Paths.get(storagePath, String.valueOf(app), IceStorageConstants.DIR_VERSIONS);
        boolean needFullLoad = false;

        // Try to load incremental updates
        for (long v = loadedVersion + 1; v <= targetVersion; v++) {
            Path updatePath = versionsPath.resolve(v + IceStorageConstants.SUFFIX_UPD);
            if (!Files.exists(updatePath)) {
                if (v == targetVersion) {
                    // Only the last version file is missing - normal case, wait for next poll
                    log.info("latest update file not ready, will retry: v{}", v);
                } else {
                    // Middle version file is missing - abnormal, need full load
                    log.warn("middle update file missing, will do full load: v{}", v);
                    needFullLoad = true;
                }
                break;
            }
            try {
                String content = new String(Files.readAllBytes(updatePath), StandardCharsets.UTF_8);
                IceTransferDto updateDto = JacksonUtils.readJson(content, IceTransferDto.class);
                if (updateDto != null) {
                    List<String> errors = IceUpdate.update(updateDto);
                    if (!errors.isEmpty()) {
                        log.warn("incremental update v{} has errors: {}", v, errors);
                    }
                    loadedVersion = v;
                    log.info("loaded incremental update version: {}", v);
                }
            } catch (Exception e) {
                log.error("failed to load incremental update v{}", v, e);
                needFullLoad = true;
                break;
            }
        }

        // If incremental load fails, perform full load
        if (needFullLoad) {
            log.info("performing full config reload");
            IceTransferDto fullDto = loadAllConfig();
            if (fullDto != null) {
                List<String> errors = IceUpdate.update(fullDto);
                if (!errors.isEmpty()) {
                    log.warn("full reload has errors: {}", errors);
                }
                loadedVersion = fullDto.getVersion();
                log.info("full config reload completed, version: {}", loadedVersion);
            }
        }

        // Update client's loaded version information
        updateClientVersion();
    }

    /**
     * Update client version information.
     */
    private void updateClientVersion() {
        try {
            writeBeatFile();
        } catch (Exception e) {
            log.error("failed to update client version info", e);
        }
    }

    /**
     * Update heartbeat.
     */
    private void updateHeartbeat() {
        try {
            if (!Files.exists(metaFilePath())) {
                registerClient();
                return;
            }
            writeBeatFile();
        } catch (Exception e) {
            log.error("failed to update heartbeat", e);
        }
    }

    /**
     * Scan leaf nodes.
     */
    private void scanLeafNodes(Set<String> scanPackages) throws IOException {
        long start = System.currentTimeMillis();
        Set<Class<?>> leafClasses;
        if (scanPackages == null || scanPackages.isEmpty()) {
            leafClasses = IceLeafScanner.scanPackage(null);
        } else {
            leafClasses = new HashSet<>();
            for (String packageName : scanPackages) {
                leafClasses.addAll(IceLeafScanner.scanPackage(packageName));
            }
        }
        log.info("ice scan leaf node, packages:{} {}ms cnt:{}", scanPackages, System.currentTimeMillis() - start, leafClasses.size());
        if (leafClasses.isEmpty()) {
            return;
        }
        leafNodes = new ArrayList<>(leafClasses.size());
        for (Class<?> leafClass : leafClasses) {
            LeafNodeInfo leafNodeInfo = new LeafNodeInfo();
            leafNodeInfo.setClazz(leafClass.getName());
            IceNode nodeAnnotation = leafClass.getAnnotation(IceNode.class);
            if (nodeAnnotation != null) {
                leafNodeInfo.setName(nodeAnnotation.name());
                leafNodeInfo.setDesc(nodeAnnotation.desc());
                leafNodeInfo.setOrder(nodeAnnotation.order());
            } else {
                leafNodeInfo.setOrder(100); // Default value
            }

            Field[] leafFields = leafClass.getDeclaredFields();
            List<LeafNodeInfo.IceFieldInfo> iceFields = new ArrayList<>();
            List<LeafNodeInfo.IceFieldInfo> hideFields = new ArrayList<>();
            for (Field field : leafFields) {
                if (Modifier.isFinal(field.getModifiers()) ||
                        Modifier.isStatic(field.getModifiers()) ||
                        field.isAnnotationPresent(IceIgnore.class) ||
                        field.isAnnotationPresent(JsonIgnore.class)) {
                    continue;
                }
                IceField fieldAnnotation = field.getAnnotation(IceField.class);
                if (fieldAnnotation != null) {
                    LeafNodeInfo.IceFieldInfo iceFieldInfo = new LeafNodeInfo.IceFieldInfo();
                    iceFieldInfo.setField(field.getName());
                    iceFieldInfo.setName(fieldAnnotation.name());
                    iceFieldInfo.setDesc(fieldAnnotation.desc());
                    iceFieldInfo.setType(fieldAnnotation.type().isEmpty() ? toUniversalTypeName(field.getType()) : fieldAnnotation.type());
                    iceFields.add(iceFieldInfo);
                } else {
                    if (field.getName().equals("log") ||
                            field.getName().equals("LOG") ||
                            field.getName().equals("logger") ||
                            field.getName().equals("LOGGER") ||
                            Logger.class.isAssignableFrom(field.getType()) ||
                            IceBeanUtils.containsBean(field.getName())) {
                        continue;
                    }
                    LeafNodeInfo.IceFieldInfo hideFieldInfo = new LeafNodeInfo.IceFieldInfo();
                    hideFieldInfo.setField(field.getName());
                    hideFieldInfo.setType(toUniversalTypeName(field.getType()));
                    hideFields.add(hideFieldInfo);
                }
            }
            if (!iceFields.isEmpty()) {
                leafNodeInfo.setIceFields(iceFields);
            }
            if (!hideFields.isEmpty()) {
                leafNodeInfo.setHideFields(hideFields);
            }
            // Scan roam key accesses via bytecode analysis
            List<LeafNodeInfo.RoamKeyMeta> roamKeys = RoamKeyScanner.scan(leafClass);
            if (!roamKeys.isEmpty()) {
                leafNodeInfo.setRoamKeys(roamKeys);
            }

            if (BaseLeafFlow.class.isAssignableFrom(leafClass)) {
                leafNodeInfo.setType(NodeTypeEnum.LEAF_FLOW.getType());
                leafNodes.add(leafNodeInfo);
                continue;
            }
            if (BaseLeafResult.class.isAssignableFrom(leafClass)) {
                leafNodeInfo.setType(NodeTypeEnum.LEAF_RESULT.getType());
                leafNodes.add(leafNodeInfo);
                continue;
            }
            if (BaseLeafNone.class.isAssignableFrom(leafClass)) {
                leafNodeInfo.setType(NodeTypeEnum.LEAF_NONE.getType());
                leafNodes.add(leafNodeInfo);
            }
        }
    }

    private Path getMockDir() {
        return Paths.get(storagePath, "mock", String.valueOf(app), safeAddress());
    }

    private void checkMocks() {
        Path mockDir = getMockDir();
        if (!Files.exists(mockDir) || !Files.isDirectory(mockDir)) {
            return;
        }

        try (java.util.stream.Stream<Path> paths = Files.list(mockDir)) {
            List<Path> mockFiles = paths
                    .filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON)
                            && !p.getFileName().toString().endsWith("_result.json"))
                    .collect(java.util.stream.Collectors.toList());
            for (Path p : mockFiles) {
                try {
                    String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    MockRequest req = JacksonUtils.readJson(content, MockRequest.class);
                    if (req == null) continue;

                    // Delete request file first to prevent re-execution on crash
                    Files.deleteIfExists(p);

                    MockResult result = executeMock(req);

                    // Write result file
                    String resultFileName = req.getMockId() + "_result" + IceStorageConstants.SUFFIX_JSON;
                    Path resultPath = mockDir.resolve(resultFileName);
                    Path tmpPath = Paths.get(resultPath.toString() + IceStorageConstants.SUFFIX_TMP);
                    String resultJson = JacksonUtils.toJsonString(result);
                    Files.write(tmpPath, resultJson.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    Files.move(tmpPath, resultPath,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);

                    log.info("mock executed mockId:{} success:{}", req.getMockId(), result.isSuccess());
                } catch (Exception e) {
                    log.error("failed to process mock file: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.error("failed to list mock directory", e);
        }
    }

    private MockResult executeMock(MockRequest req) {
        MockResult result = new MockResult();
        result.setMockId(req.getMockId());
        result.setExecuteAt(System.currentTimeMillis());
        IceRoam roam = IceRoam.create();

        try {
            IceMeta meta = roam.getIceMeta();
            meta.setId(req.getIceId());
            meta.setNid(req.getConfId());
            meta.setScene(req.getScene());
            meta.setDebug(req.getDebug());
            if (req.getTs() > 0) {
                meta.setTs(req.getTs());
            }

            // Put user roam data
            if (req.getRoam() != null) {
                for (Map.Entry<String, Object> entry : req.getRoam().entrySet()) {
                    roam.put(entry.getKey(), entry.getValue());
                }
            }

            // Dispatch using cache directly (same logic as Go executeMock)
            boolean handled = false;
            if (meta.getId() > 0 && meta.getNid() > 0) {
                // Both iceId and confId: get handler by iceId, find confId subtree
                IceHandler handler = IceHandlerCache.getHandlerById(meta.getId());
                if (handler != null && handler.getRoot() != null) {
                    if (meta.getDebug() == 0) {
                        meta.setDebug(handler.getDebug());
                    }
                    BaseNode subtree = findNodeById(handler.getRoot(), meta.getNid());
                    if (subtree != null) {
                        IceHandler sub = new IceHandler();
                        sub.setDebug(meta.getDebug());
                        sub.setRoot(subtree);
                        sub.setConfId(meta.getNid());
                        sub.handleWithNodeId(roam);
                        handled = true;
                    }
                }
            } else if (meta.getId() > 0) {
                IceHandler handler = IceHandlerCache.getHandlerById(meta.getId());
                if (handler != null) {
                    if (meta.getDebug() == 0) {
                        meta.setDebug(handler.getDebug());
                    }
                    handler.handle(roam);
                    handled = true;
                }
            } else if (meta.getScene() != null && !meta.getScene().isEmpty()) {
                Map<Long, IceHandler> handlerMap = IceHandlerCache.getHandlersByScene(meta.getScene());
                if (handlerMap != null && !handlerMap.isEmpty()) {
                    for (IceHandler handler : handlerMap.values()) {
                        if (meta.getDebug() == 0) {
                            meta.setDebug(handler.getDebug());
                        }
                        meta.setId(handler.findIceId());
                        handler.handle(roam);
                        handled = true;
                        break; // mock only handles first matching handler
                    }
                }
            } else if (meta.getNid() > 0) {
                BaseNode root = IceConfCache.getConfById(meta.getNid());
                if (root != null) {
                    IceHandler handler = new IceHandler();
                    handler.setDebug(meta.getDebug());
                    handler.setRoot(root);
                    handler.setConfId(meta.getNid());
                    handler.handleWithNodeId(roam);
                    handled = true;
                }
            }

            if (!handled) {
                result.setSuccess(false);
                result.setError("no matching handler found");
                result.setTrace(roam.getIceTrace());
                result.setTs(roam.getIceTs());
                return result;
            }

            result.setSuccess(true);
            result.setTrace(roam.getIceTrace());
            result.setTs(roam.getIceTs());
            HashMap<String, Object> roamData = new HashMap<>(roam);
            roamData.remove("_ice");
            result.setRoam(roamData);

            StringBuilder process = roam.getIceProcess();
            if (process != null) {
                result.setProcess(process.toString());
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setError(e.getMessage());
            result.setTrace(roam.getIceTrace());
            result.setTs(roam.getIceTs());
        }

        return result;
    }

    /**
     * Walk the tree to find a node by its ID.
     */
    private static BaseNode findNodeById(BaseNode node, long id) {
        if (node == null) {
            return null;
        }
        if (node.getIceNodeId() == id) {
            return node;
        }
        // Check forward
        if (node.getIceForward() != null) {
            BaseNode found = findNodeById(node.getIceForward(), id);
            if (found != null) {
                return found;
            }
        }
        // Check children (relation nodes)
        if (node instanceof BaseRelation) {
            IceLinkedList<BaseNode> children = ((BaseRelation) node).getIceChildren();
            if (children != null) {
                for (IceLinkedList.Node<BaseNode> x = children.getFirst(); x != null; x = x.next) {
                    BaseNode found = findNodeById(x.item, id);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    public String getIceAddress() {
        return iceAddress;
    }

    public List<LeafNodeInfo> getLeafNodes() {
        return leafNodes;
    }

    public int getApp() {
        return app;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public long getLoadedVersion() {
        return loadedVersion;
    }

    public String getLane() {
        return lane;
    }

    /**
     * Convert Java type to language-agnostic universal type name.
     * This ensures consistent type names across Java, Go, and Python SDKs.
     */
    private static String toUniversalTypeName(Class<?> type) {
        if (type == String.class || type == Character.class || type == char.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class || type == short.class || type == Short.class || type == byte.class || type == Byte.class) {
            return "int";
        }
        if (type == long.class || type == Long.class) {
            return "long";
        }
        if (type == double.class || type == Double.class) {
            return "double";
        }
        if (type == float.class || type == Float.class) {
            return "float";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type.isArray() || java.util.Collection.class.isAssignableFrom(type)) {
            return "list";
        }
        if (java.util.Map.class.isAssignableFrom(type)) {
            return "map";
        }
        return "object";
    }
}


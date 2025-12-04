package com.ice.core.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ice.common.constant.Constant;
import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceClientInfo;
import com.ice.common.dto.IceConfDto;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.LeafNodeInfo;
import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceIgnore;
import com.ice.core.annotation.IceNode;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.utils.IceAddressUtils;
import com.ice.core.utils.IceBeanUtils;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于文件系统的Ice客户端
 * 替代原来的IceNioClient
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

    private List<LeafNodeInfo> leafNodes;
    private volatile long loadedVersion = 0;
    private volatile boolean started = false;
    private volatile boolean destroy = false;

    private final AtomicBoolean startedLock = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    // 默认配置
    private static final int DEFAULT_PARALLELISM = -1;

    public IceFileClient(int app, String storagePath, int parallelism, Set<String> scanPackages,
                         int pollIntervalSeconds, int heartbeatIntervalSeconds) throws IOException {
        this.app = app;
        this.storagePath = storagePath;
        this.parallelism = parallelism;
        this.pollIntervalSeconds = pollIntervalSeconds > 0 ? pollIntervalSeconds : IceStorageConstants.DEFAULT_POLL_INTERVAL_SECONDS;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds > 0 ? heartbeatIntervalSeconds : IceStorageConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS;
        this.iceAddress = IceAddressUtils.getAddress(app);

        scanLeafNodes(scanPackages);
        prepare();
    }

    public IceFileClient(int app, String storagePath, Set<String> scanPackages) throws IOException {
        this(app, storagePath, DEFAULT_PARALLELISM, scanPackages,
                IceStorageConstants.DEFAULT_POLL_INTERVAL_SECONDS,
                IceStorageConstants.DEFAULT_HEARTBEAT_INTERVAL_SECONDS);
    }

    public IceFileClient(int app, String storagePath, String scan) throws IOException {
        this(app, storagePath, new HashSet<>(Arrays.asList(scan.split(Constant.REGEX_COMMA))));
    }

    public IceFileClient(int app, String storagePath) throws IOException {
        this(app, storagePath, Collections.emptySet());
    }

    private void prepare() {
        if (parallelism <= 0) {
            IceExecutor.setExecutor(new ForkJoinPool());
        } else {
            IceExecutor.setExecutor(new ForkJoinPool(parallelism));
        }
        scheduler = new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r, "ice-file-client-" + app);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动客户端
     * 1. 从文件系统加载配置
     * 2. 注册客户端信息
     * 3. 启动版本轮询和心跳任务
     */
    public void start() throws Exception {
        destroy = false;
        long startTime = System.currentTimeMillis();

        // 确保目录存在
        ensureDirectories();

        // 加载初始配置
        loadInitialConfig();

        // 注册客户端信息
        registerClient();

        // 启动版本轮询
        startVersionPoller();

        // 启动心跳上报
        startHeartbeat();

        started = true;
        startedLock.set(true);
        log.info("ice file client init app:{} address:{} success:{}ms storagePath:{}",
                app, iceAddress, System.currentTimeMillis() - startTime, storagePath);
    }

    /**
     * 等待启动完成
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
     * 销毁客户端
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

        // 清理客户端文件
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

        Path clientsPath = Paths.get(storagePath, IceStorageConstants.DIR_CLIENTS, String.valueOf(app));
        Files.createDirectories(clientsPath);
    }

    /**
     * 加载初始配置
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
     * 加载所有配置
     */
    private IceTransferDto loadAllConfig() throws IOException {
        IceTransferDto dto = new IceTransferDto();
        Path appPath = Paths.get(storagePath, String.valueOf(app));

        // 读取版本号
        Path versionPath = appPath.resolve(IceStorageConstants.FILE_VERSION);
        if (Files.exists(versionPath)) {
            String versionStr = new String(Files.readAllBytes(versionPath), StandardCharsets.UTF_8).trim();
            dto.setVersion(Long.parseLong(versionStr));
        } else {
            dto.setVersion(0);
        }

        // 读取所有base
        Path basesPath = appPath.resolve(IceStorageConstants.DIR_BASES);
        List<IceBaseDto> bases = new ArrayList<>();
        if (Files.exists(basesPath)) {
            Files.list(basesPath)
                    .filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
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

        // 读取所有conf
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

    /**
     * 注册客户端信息
     */
    private void registerClient() throws IOException {
        IceClientInfo clientInfo = new IceClientInfo();
        clientInfo.setAddress(iceAddress);
        clientInfo.setApp(app);
        clientInfo.setLeafNodes(leafNodes);
        clientInfo.setLastHeartbeat(System.currentTimeMillis());
        clientInfo.setStartTime(System.currentTimeMillis());
        clientInfo.setLoadedVersion(loadedVersion);

        writeClientInfo(clientInfo);
        log.info("ice client registered: {}", iceAddress);
    }

    /**
     * 注销客户端
     */
    private void unregisterClient() {
        try {
            Path clientPath = getClientFilePath();
            if (Files.exists(clientPath)) {
                Files.delete(clientPath);
                log.info("ice client unregistered: {}", iceAddress);
            }
        } catch (IOException e) {
            log.error("failed to unregister client", e);
        }
    }

    /**
     * 写入客户端信息
     */
    private void writeClientInfo(IceClientInfo clientInfo) throws IOException {
        Path clientPath = getClientFilePath();
        String json = JacksonUtils.toJsonString(clientInfo);

        // 确保目录存在
        Files.createDirectories(clientPath.getParent());

        // 使用临时文件+rename确保原子性
        Path tmpPath = Paths.get(clientPath.toString() + IceStorageConstants.SUFFIX_TMP);
        Files.write(tmpPath, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpPath, clientPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);

        // 如果有 leafNodes，更新 _latest.json（只在不存在时写入，避免频繁更新）
        if (leafNodes != null && !leafNodes.isEmpty()) {
            Path latestPath = clientPath.getParent().resolve("_latest.json");
            if (!Files.exists(latestPath)) {
                Path latestTmpPath = Paths.get(latestPath.toString() + IceStorageConstants.SUFFIX_TMP);
                Files.write(latestTmpPath, json.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                try {
                    Files.move(latestTmpPath, latestPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    log.info("ice client created _latest.json for app:{}", app);
                } catch (java.nio.file.FileAlreadyExistsException e) {
                    // 并发情况下可能已被其他客户端创建，忽略
                    Files.deleteIfExists(latestTmpPath);
                }
            }
        }
    }

    private Path getClientFilePath() {
        // 将地址中的特殊字符替换，确保可以作为文件名
        String safeAddress = iceAddress.replace(":", "_").replace("/", "_");
        return Paths.get(storagePath, IceStorageConstants.DIR_CLIENTS, String.valueOf(app),
                safeAddress + IceStorageConstants.SUFFIX_JSON);
    }

    /**
     * 启动版本轮询
     */
    private void startVersionPoller() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (destroy) return;
            try {
                checkAndUpdateVersion();
            } catch (Exception e) {
                log.error("version poll error", e);
            }
        }, pollIntervalSeconds, pollIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 检查并更新版本
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
     * 加载增量更新
     */
    private void loadIncrementalUpdates(long targetVersion) throws IOException {
        Path versionsPath = Paths.get(storagePath, String.valueOf(app), IceStorageConstants.DIR_VERSIONS);
        boolean needFullLoad = false;

        // 尝试加载增量更新
        for (long v = loadedVersion + 1; v <= targetVersion; v++) {
            Path updatePath = versionsPath.resolve(v + IceStorageConstants.SUFFIX_UPD);
            if (Files.exists(updatePath)) {
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
            } else {
                log.warn("incremental update file not found: v{}, will do full load", v);
                needFullLoad = true;
                break;
            }
        }

        // 如果增量加载失败，则全量加载
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

        // 更新客户端的加载版本信息
        updateClientVersion();
    }

    /**
     * 更新客户端版本信息
     */
    private void updateClientVersion() {
        try {
            Path clientPath = getClientFilePath();
            if (Files.exists(clientPath)) {
                String content = new String(Files.readAllBytes(clientPath), StandardCharsets.UTF_8);
                IceClientInfo clientInfo = JacksonUtils.readJson(content, IceClientInfo.class);
                if (clientInfo != null) {
                    clientInfo.setLoadedVersion(loadedVersion);
                    clientInfo.setLastHeartbeat(System.currentTimeMillis());
                    writeClientInfo(clientInfo);
                }
            }
        } catch (Exception e) {
            log.error("failed to update client version info", e);
        }
    }

    /**
     * 启动心跳上报
     */
    private void startHeartbeat() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (destroy) return;
            try {
                updateHeartbeat();
            } catch (Exception e) {
                log.error("heartbeat error", e);
            }
        }, heartbeatIntervalSeconds, heartbeatIntervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * 更新心跳
     */
    private void updateHeartbeat() {
        try {
            Path clientPath = getClientFilePath();
            if (Files.exists(clientPath)) {
                String content = new String(Files.readAllBytes(clientPath), StandardCharsets.UTF_8);
                IceClientInfo clientInfo = JacksonUtils.readJson(content, IceClientInfo.class);
                if (clientInfo != null) {
                    clientInfo.setLastHeartbeat(System.currentTimeMillis());
                    writeClientInfo(clientInfo);
                }
            } else {
                // 文件不存在，重新注册
                registerClient();
            }
        } catch (Exception e) {
            log.error("failed to update heartbeat", e);
        }
    }

    /**
     * 扫描叶子节点
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
                    iceFieldInfo.setType(fieldAnnotation.type().isEmpty() ? field.getType().getTypeName() : fieldAnnotation.type());
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
                    hideFieldInfo.setType(field.getType().getTypeName());
                    hideFields.add(hideFieldInfo);
                }
            }
            if (!iceFields.isEmpty()) {
                leafNodeInfo.setIceFields(iceFields);
            }
            if (!hideFields.isEmpty()) {
                leafNodeInfo.setHideFields(hideFields);
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
}


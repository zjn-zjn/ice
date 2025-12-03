package com.ice.server.storage;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.*;
import com.ice.core.utils.JacksonUtils;
import com.ice.server.config.IceServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件系统存储服务
 * 提供对各类配置数据的CRUD操作
 *
 * @author waitmoon
 */
@Slf4j
@Service
public class IceFileStorageService {

    private final IceServerProperties properties;
    private Path storagePath;

    // ID生成器缓存
    private IceIdGenerator appIdGenerator;
    private final Map<Integer, IceIdGenerator> baseIdGenerators = new ConcurrentHashMap<>();
    private final Map<Integer, IceIdGenerator> confIdGenerators = new ConcurrentHashMap<>();
    private final Map<Integer, IceIdGenerator> pushIdGenerators = new ConcurrentHashMap<>();

    public IceFileStorageService(IceServerProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        this.storagePath = Paths.get(properties.getStorage().getPath());
        Files.createDirectories(storagePath);
        Files.createDirectories(storagePath.resolve(IceStorageConstants.DIR_APPS));
        Files.createDirectories(storagePath.resolve(IceStorageConstants.DIR_CLIENTS));

        // 初始化app ID生成器
        appIdGenerator = new IceIdGenerator(storagePath.resolve(IceStorageConstants.DIR_APPS)
                .resolve(IceStorageConstants.FILE_APP_ID));

        log.info("ice file storage initialized at: {}", storagePath.toAbsolutePath());
    }

    public Path getStoragePath() {
        return storagePath;
    }

    // ==================== App 操作 ====================

    public int nextAppId() throws IOException {
        return (int) appIdGenerator.nextId();
    }

    public void saveApp(IceAppDto app) throws IOException {
        Path appPath = storagePath.resolve(IceStorageConstants.DIR_APPS)
                .resolve(app.getId() + IceStorageConstants.SUFFIX_JSON);
        writeJsonFile(appPath, app);
    }

    public IceAppDto getApp(int appId) throws IOException {
        Path appPath = storagePath.resolve(IceStorageConstants.DIR_APPS)
                .resolve(appId + IceStorageConstants.SUFFIX_JSON);
        return readJsonFile(appPath, IceAppDto.class);
    }

    public List<IceAppDto> listApps() throws IOException {
        Path appsDir = storagePath.resolve(IceStorageConstants.DIR_APPS);
        if (!Files.exists(appsDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(appsDir)) {
            return paths.filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .map(p -> {
                        try {
                            IceAppDto app = readJsonFile(p, IceAppDto.class);
                            if (app != null && app.getStatus() != null
                                    && app.getStatus() != IceStorageConstants.STATUS_DELETED) {
                                return app;
                            }
                        } catch (IOException e) {
                            log.error("failed to read app file: {}", p, e);
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    // ==================== Base 操作 ====================

    private IceIdGenerator getBaseIdGenerator(int app) throws IOException {
        return baseIdGenerators.computeIfAbsent(app, a -> {
            try {
                ensureAppDirectories(a);
                return new IceIdGenerator(getAppPath(a).resolve(IceStorageConstants.FILE_BASE_ID));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public long nextBaseId(int app) throws IOException {
        return getBaseIdGenerator(app).nextId();
    }

    public void saveBase(IceBaseDto base) throws IOException {
        ensureAppDirectories(base.getApp());
        Path basePath = getAppPath(base.getApp()).resolve(IceStorageConstants.DIR_BASES)
                .resolve(base.getId() + IceStorageConstants.SUFFIX_JSON);
        writeJsonFile(basePath, base);
    }

    public IceBaseDto getBase(int app, long baseId) throws IOException {
        Path basePath = getAppPath(app).resolve(IceStorageConstants.DIR_BASES)
                .resolve(baseId + IceStorageConstants.SUFFIX_JSON);
        return readJsonFile(basePath, IceBaseDto.class);
    }

    public List<IceBaseDto> listBases(int app) throws IOException {
        Path basesDir = getAppPath(app).resolve(IceStorageConstants.DIR_BASES);
        if (!Files.exists(basesDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(basesDir)) {
            return paths.filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .map(p -> {
                        try {
                            return readJsonFile(p, IceBaseDto.class);
                        } catch (IOException e) {
                            log.error("failed to read base file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    public List<IceBaseDto> listActiveBases(int app) throws IOException {
        return listBases(app).stream()
                .filter(b -> b.getStatus() != null && b.getStatus() == IceStorageConstants.STATUS_ONLINE)
                .collect(Collectors.toList());
    }

    public void deleteBase(int app, long baseId, boolean hard) throws IOException {
        Path basePath = getAppPath(app).resolve(IceStorageConstants.DIR_BASES)
                .resolve(baseId + IceStorageConstants.SUFFIX_JSON);

        if (hard) {
            Files.deleteIfExists(basePath);
        } else {
            IceBaseDto base = readJsonFile(basePath, IceBaseDto.class);
            if (base != null) {
                base.setStatus(IceStorageConstants.STATUS_DELETED);
                base.setUpdateAt(System.currentTimeMillis());
                writeJsonFile(basePath, base);
            }
        }
    }

    // ==================== Conf 操作 ====================

    private IceIdGenerator getConfIdGenerator(int app) throws IOException {
        return confIdGenerators.computeIfAbsent(app, a -> {
            try {
                ensureAppDirectories(a);
                return new IceIdGenerator(getAppPath(a).resolve(IceStorageConstants.FILE_CONF_ID));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public long nextConfId(int app) throws IOException {
        return getConfIdGenerator(app).nextId();
    }

    public void saveConf(IceConfDto conf) throws IOException {
        ensureAppDirectories(conf.getApp());
        Path confPath = getAppPath(conf.getApp()).resolve(IceStorageConstants.DIR_CONFS)
                .resolve(conf.getId() + IceStorageConstants.SUFFIX_JSON);
        writeJsonFile(confPath, conf);
    }

    public IceConfDto getConf(int app, long confId) throws IOException {
        Path confPath = getAppPath(app).resolve(IceStorageConstants.DIR_CONFS)
                .resolve(confId + IceStorageConstants.SUFFIX_JSON);
        return readJsonFile(confPath, IceConfDto.class);
    }

    public List<IceConfDto> listConfs(int app) throws IOException {
        Path confsDir = getAppPath(app).resolve(IceStorageConstants.DIR_CONFS);
        if (!Files.exists(confsDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(confsDir)) {
            return paths.filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .map(p -> {
                        try {
                            return readJsonFile(p, IceConfDto.class);
                        } catch (IOException e) {
                            log.error("failed to read conf file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    public List<IceConfDto> listActiveConfs(int app) throws IOException {
        return listConfs(app).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() != IceStorageConstants.STATUS_DELETED)
                .collect(Collectors.toList());
    }

    public void deleteConf(int app, long confId, boolean hard) throws IOException {
        Path confPath = getAppPath(app).resolve(IceStorageConstants.DIR_CONFS)
                .resolve(confId + IceStorageConstants.SUFFIX_JSON);

        if (hard) {
            Files.deleteIfExists(confPath);
        } else {
            IceConfDto conf = readJsonFile(confPath, IceConfDto.class);
            if (conf != null) {
                conf.setStatus(IceStorageConstants.STATUS_DELETED);
                conf.setUpdateAt(System.currentTimeMillis());
                writeJsonFile(confPath, conf);
            }
        }
    }

    // ==================== ConfUpdate 操作 ====================

    public void saveConfUpdate(int app, long iceId, IceConfDto confUpdate) throws IOException {
        ensureAppDirectories(app);
        Path updateDir = getAppPath(app).resolve(IceStorageConstants.DIR_UPDATES).resolve(String.valueOf(iceId));
        Files.createDirectories(updateDir);

        Path updatePath = updateDir.resolve(confUpdate.getConfId() + IceStorageConstants.SUFFIX_JSON);
        writeJsonFile(updatePath, confUpdate);
    }

    public IceConfDto getConfUpdate(int app, long iceId, long confId) throws IOException {
        Path updatePath = getAppPath(app).resolve(IceStorageConstants.DIR_UPDATES)
                .resolve(String.valueOf(iceId))
                .resolve(confId + IceStorageConstants.SUFFIX_JSON);
        return readJsonFile(updatePath, IceConfDto.class);
    }

    public List<IceConfDto> listConfUpdates(int app, long iceId) throws IOException {
        Path updateDir = getAppPath(app).resolve(IceStorageConstants.DIR_UPDATES).resolve(String.valueOf(iceId));
        if (!Files.exists(updateDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(updateDir)) {
            return paths.filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .map(p -> {
                        try {
                            return readJsonFile(p, IceConfDto.class);
                        } catch (IOException e) {
                            log.error("failed to read conf update file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    public void deleteConfUpdate(int app, long iceId, long confId) throws IOException {
        Path updatePath = getAppPath(app).resolve(IceStorageConstants.DIR_UPDATES)
                .resolve(String.valueOf(iceId))
                .resolve(confId + IceStorageConstants.SUFFIX_JSON);
        Files.deleteIfExists(updatePath);
    }

    public void deleteAllConfUpdates(int app, long iceId) throws IOException {
        Path updateDir = getAppPath(app).resolve(IceStorageConstants.DIR_UPDATES).resolve(String.valueOf(iceId));
        if (Files.exists(updateDir)) {
            try (Stream<Path> paths = Files.list(updateDir)) {
                paths.forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.error("failed to delete conf update file: {}", p, e);
                    }
                });
            }
            Files.deleteIfExists(updateDir);
        }
    }

    // ==================== PushHistory 操作 ====================

    private IceIdGenerator getPushIdGenerator(int app) throws IOException {
        return pushIdGenerators.computeIfAbsent(app, a -> {
            try {
                ensureAppDirectories(a);
                return new IceIdGenerator(getAppPath(a).resolve(IceStorageConstants.FILE_PUSH_ID));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public long nextPushId(int app) throws IOException {
        return getPushIdGenerator(app).nextId();
    }

    public void savePushHistory(IcePushHistoryDto history) throws IOException {
        ensureAppDirectories(history.getApp());
        Path historyPath = getAppPath(history.getApp()).resolve(IceStorageConstants.DIR_HISTORY)
                .resolve(history.getId() + IceStorageConstants.SUFFIX_JSON);
        writeJsonFile(historyPath, history);
    }

    public IcePushHistoryDto getPushHistory(int app, long pushId) throws IOException {
        Path historyPath = getAppPath(app).resolve(IceStorageConstants.DIR_HISTORY)
                .resolve(pushId + IceStorageConstants.SUFFIX_JSON);
        return readJsonFile(historyPath, IcePushHistoryDto.class);
    }

    public List<IcePushHistoryDto> listPushHistories(int app, Long iceId) throws IOException {
        Path historyDir = getAppPath(app).resolve(IceStorageConstants.DIR_HISTORY);
        if (!Files.exists(historyDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(historyDir)) {
            return paths.filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .map(p -> {
                        try {
                            return readJsonFile(p, IcePushHistoryDto.class);
                        } catch (IOException e) {
                            log.error("failed to read push history file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(h -> iceId == null || iceId.equals(h.getIceId()))
                    .sorted((a, b) -> Long.compare(b.getCreateAt(), a.getCreateAt()))
                    .collect(Collectors.toList());
        }
    }

    public void deletePushHistory(int app, long pushId) throws IOException {
        Path historyPath = getAppPath(app).resolve(IceStorageConstants.DIR_HISTORY)
                .resolve(pushId + IceStorageConstants.SUFFIX_JSON);
        Files.deleteIfExists(historyPath);
    }

    // ==================== Version 操作 ====================

    public long getVersion(int app) throws IOException {
        Path versionPath = getAppPath(app).resolve(IceStorageConstants.FILE_VERSION);
        if (!Files.exists(versionPath)) {
            return 0;
        }
        String content = new String(Files.readAllBytes(versionPath), StandardCharsets.UTF_8).trim();
        return StringUtils.hasLength(content) ? Long.parseLong(content) : 0;
    }

    public void setVersion(int app, long version) throws IOException {
        ensureAppDirectories(app);
        Path versionPath = getAppPath(app).resolve(IceStorageConstants.FILE_VERSION);
        Path tmpPath = versionPath.resolveSibling(IceStorageConstants.FILE_VERSION + IceStorageConstants.SUFFIX_TMP);

        Files.write(tmpPath, String.valueOf(version).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpPath, versionPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public void saveVersionUpdate(int app, long version, IceTransferDto updateDto) throws IOException {
        ensureAppDirectories(app);
        Path versionsDir = getAppPath(app).resolve(IceStorageConstants.DIR_VERSIONS);
        Files.createDirectories(versionsDir);

        Path updatePath = versionsDir.resolve(version + IceStorageConstants.SUFFIX_UPD);
        writeJsonFile(updatePath, updateDto);
    }

    public void cleanOldVersions(int app, int retention) throws IOException {
        Path versionsDir = getAppPath(app).resolve(IceStorageConstants.DIR_VERSIONS);
        if (!Files.exists(versionsDir)) {
            return;
        }

        long currentVersion = getVersion(app);
        long threshold = currentVersion - retention;

        try (Stream<Path> paths = Files.list(versionsDir)) {
            paths.filter(p -> {
                        String fileName = p.getFileName().toString();
                        if (fileName.endsWith(IceStorageConstants.SUFFIX_UPD)) {
                            String versionStr = fileName.substring(0, fileName.length() - IceStorageConstants.SUFFIX_UPD.length());
                            try {
                                long v = Long.parseLong(versionStr);
                                return v < threshold;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        return false;
                    })
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.debug("deleted old version file: {}", p);
                        } catch (IOException e) {
                            log.error("failed to delete old version file: {}", p, e);
                        }
                    });
        }
    }

    // ==================== Client 操作 ====================

    public void saveClient(IceClientInfo client) throws IOException {
        Path clientsDir = storagePath.resolve(IceStorageConstants.DIR_CLIENTS).resolve(String.valueOf(client.getApp()));
        Files.createDirectories(clientsDir);

        String safeAddress = client.getAddress().replace(":", "_").replace("/", "_");
        Path clientPath = clientsDir.resolve(safeAddress + IceStorageConstants.SUFFIX_JSON);
        writeJsonFile(clientPath, client);
    }

    public List<IceClientInfo> listClients(int app) throws IOException {
        Path clientsDir = storagePath.resolve(IceStorageConstants.DIR_CLIENTS).resolve(String.valueOf(app));
        if (!Files.exists(clientsDir)) {
            return Collections.emptyList();
        }

        try (Stream<Path> paths = Files.list(clientsDir)) {
            return paths.filter(p -> p.toString().endsWith(IceStorageConstants.SUFFIX_JSON))
                    .map(p -> {
                        try {
                            return readJsonFile(p, IceClientInfo.class);
                        } catch (IOException e) {
                            log.error("failed to read client file: {}", p, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }
    }

    public void deleteClient(int app, String address) throws IOException {
        Path clientsDir = storagePath.resolve(IceStorageConstants.DIR_CLIENTS).resolve(String.valueOf(app));
        String safeAddress = address.replace(":", "_").replace("/", "_");
        Path clientPath = clientsDir.resolve(safeAddress + IceStorageConstants.SUFFIX_JSON);
        Files.deleteIfExists(clientPath);
    }

    // ==================== 辅助方法 ====================

    private Path getAppPath(int app) {
        return storagePath.resolve(String.valueOf(app));
    }

    public void ensureAppDirectories(int app) throws IOException {
        Path appPath = getAppPath(app);
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_BASES));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_CONFS));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_UPDATES));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_VERSIONS));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_HISTORY));
    }

    private <T> void writeJsonFile(Path path, T data) throws IOException {
        String json = JacksonUtils.toJsonString(data);
        Path tmpPath = path.resolveSibling(path.getFileName() + IceStorageConstants.SUFFIX_TMP);

        Files.createDirectories(path.getParent());
        Files.write(tmpPath, json.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmpPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private <T> T readJsonFile(Path path, Class<T> clazz) throws IOException {
        if (!Files.exists(path)) {
            return null;
        }
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return JacksonUtils.readJson(content, clazz);
    }
}


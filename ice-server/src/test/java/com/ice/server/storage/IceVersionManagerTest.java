package com.ice.server.storage;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.*;
import com.ice.server.config.IceServerProperties;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 版本管理测试
 *
 * @author waitmoon
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IceVersionManagerTest {

    private static IceFileStorageService storageService;
    private static IceServerProperties properties;
    private static Path testStoragePath;

    @BeforeAll
    static void setup() throws IOException {
        testStoragePath = Files.createTempDirectory("ice-version-test");

        properties = new IceServerProperties();
        properties.getStorage().setPath(testStoragePath.toString());
        properties.setVersionRetention(5); // 只保留5个版本

        storageService = new IceFileStorageService(properties);
        storageService.init();

        // 创建测试app
        IceAppDto app = new IceAppDto();
        app.setId(1);
        app.setName("test-app");
        app.setStatus(IceStorageConstants.STATUS_ONLINE);
        app.setCreateAt(System.currentTimeMillis());
        storageService.saveApp(app);
        storageService.ensureAppDirectories(1);
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (testStoragePath != null && Files.exists(testStoragePath)) {
            Files.walkFileTree(testStoragePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    @Test
    @Order(1)
    void testVersionInitialization() throws IOException {
        // 初始版本应为0
        long version = storageService.getVersion(1);
        assertEquals(0, version);
    }

    @Test
    @Order(2)
    void testVersionIncrement() throws IOException {
        // 递增版本
        storageService.setVersion(1, 1);
        assertEquals(1, storageService.getVersion(1));

        storageService.setVersion(1, 2);
        assertEquals(2, storageService.getVersion(1));
    }

    @Test
    @Order(3)
    void testVersionUpdateFile() throws IOException {
        // 创建版本更新文件
        IceTransferDto updateDto = new IceTransferDto();
        updateDto.setVersion(3L);

        IceConfDto conf = new IceConfDto();
        conf.setId(1L);
        conf.setApp(1);
        conf.setName("test-conf");
        conf.setType((byte) 1);
        conf.setStatus(IceStorageConstants.STATUS_ONLINE);
        updateDto.setInsertOrUpdateConfs(Collections.singletonList(conf));

        storageService.setVersion(1, 3);
        storageService.saveVersionUpdate(1, 3, updateDto);

        // 验证版本更新文件存在
        Path updatePath = testStoragePath.resolve("1")
                .resolve(IceStorageConstants.DIR_VERSIONS)
                .resolve("3" + IceStorageConstants.SUFFIX_UPD);
        assertTrue(Files.exists(updatePath));
    }

    @Test
    @Order(4)
    void testMultipleVersionUpdates() throws IOException {
        // 创建多个版本更新
        for (int v = 4; v <= 10; v++) {
            IceTransferDto updateDto = new IceTransferDto();
            updateDto.setVersion((long) v);

            storageService.setVersion(1, v);
            storageService.saveVersionUpdate(1, v, updateDto);
        }

        assertEquals(10, storageService.getVersion(1));

        // 验证所有版本文件存在
        Path versionsDir = testStoragePath.resolve("1").resolve(IceStorageConstants.DIR_VERSIONS);
        long fileCount = Files.list(versionsDir).count();
        assertEquals(8, fileCount); // v3 到 v10
    }

    @Test
    @Order(5)
    void testOldVersionCleanup() throws IOException {
        // 清理旧版本（保留5个）
        storageService.cleanOldVersions(1, 5);

        // 验证旧版本被清理
        Path versionsDir = testStoragePath.resolve("1").resolve(IceStorageConstants.DIR_VERSIONS);

        // 应该保留最近5个版本 (current=10, threshold=10-5=5, 即 <5 的删除)
        // v3, v4 应该被删除
        assertFalse(Files.exists(versionsDir.resolve("3" + IceStorageConstants.SUFFIX_UPD)));
        assertFalse(Files.exists(versionsDir.resolve("4" + IceStorageConstants.SUFFIX_UPD)));
        // v5及以上应该保留
        assertTrue(Files.exists(versionsDir.resolve("6" + IceStorageConstants.SUFFIX_UPD)));
        assertTrue(Files.exists(versionsDir.resolve("10" + IceStorageConstants.SUFFIX_UPD)));
    }

    @Test
    @Order(6)
    void testAtomicVersionWrite() throws IOException {
        // 测试原子写入 - 确保没有临时文件残留
        storageService.setVersion(1, 11);

        Path versionDir = testStoragePath.resolve("1");
        Path tmpFile = versionDir.resolve(IceStorageConstants.FILE_VERSION + IceStorageConstants.SUFFIX_TMP);

        assertFalse(Files.exists(tmpFile), "临时文件不应该残留");
        assertEquals(11, storageService.getVersion(1));
    }
}


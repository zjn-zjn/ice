package com.ice.core.client;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import com.ice.core.utils.JacksonUtils;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IceFileClient 测试
 *
 * @author waitmoon
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IceFileClientTest {

    private static Path testStoragePath;
    private static final int TEST_APP = 1;

    @BeforeAll
    static void setup() throws IOException {
        testStoragePath = Files.createTempDirectory("ice-file-client-test");

        // 创建目录结构
        Path appPath = testStoragePath.resolve(String.valueOf(TEST_APP));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_BASES));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_CONFS));
        Files.createDirectories(appPath.resolve(IceStorageConstants.DIR_VERSIONS));
        Files.createDirectories(testStoragePath.resolve(IceStorageConstants.DIR_CLIENTS).resolve(String.valueOf(TEST_APP)));

        // 创建测试数据
        createTestData();
    }

    static void createTestData() throws IOException {
        Path appPath = testStoragePath.resolve(String.valueOf(TEST_APP));

        // 创建version文件
        Files.write(appPath.resolve(IceStorageConstants.FILE_VERSION), "1".getBytes(StandardCharsets.UTF_8));

        // 创建base
        IceBaseDto base = new IceBaseDto();
        base.setId(1L);
        base.setApp(TEST_APP);
        base.setName("test-base");
        base.setScenes("scene1");
        base.setConfId(1L);
        base.setStatus(IceStorageConstants.STATUS_ONLINE);
        base.setTimeType((byte) 1);
        base.setDebug((byte) 1);
        base.setPriority(1L);
        base.setCreateAt(System.currentTimeMillis());
        base.setUpdateAt(System.currentTimeMillis());

        Path basePath = appPath.resolve(IceStorageConstants.DIR_BASES).resolve("1.json");
        Files.write(basePath, JacksonUtils.toJsonString(base).getBytes(StandardCharsets.UTF_8));

        // 创建conf
        IceConfDto conf = new IceConfDto();
        conf.setId(1L);
        conf.setApp(TEST_APP);
        conf.setName("test-root");
        conf.setType((byte) 1); // NONE
        conf.setStatus(IceStorageConstants.STATUS_ONLINE);
        conf.setDebug((byte) 1);
        conf.setTimeType((byte) 1);
        conf.setInverse(false);
        conf.setCreateAt(System.currentTimeMillis());
        conf.setUpdateAt(System.currentTimeMillis());

        Path confPath = appPath.resolve(IceStorageConstants.DIR_CONFS).resolve("1.json");
        Files.write(confPath, JacksonUtils.toJsonString(conf).getBytes(StandardCharsets.UTF_8));
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
    void testClientCreation() throws Exception {
        IceFileClient client = new IceFileClient(
                TEST_APP,
                testStoragePath.toString(),
                -1,
                Collections.emptySet(),
                5,
                10
        );

        assertNotNull(client);
        assertEquals(TEST_APP, client.getApp());
        assertEquals(testStoragePath.toString(), client.getStoragePath());
    }

    @Test
    @Order(2)
    void testClientStartAndDestroy() throws Exception {
        IceFileClient client = new IceFileClient(
                TEST_APP,
                testStoragePath.toString(),
                -1,
                Collections.emptySet(),
                5,
                10
        );

        client.start();

        // 验证客户端已启动
        assertTrue(client.getLoadedVersion() >= 0);
        assertNotNull(client.getIceAddress());

        // 验证客户端信息文件存在
        String safeAddress = client.getIceAddress().replace(":", "_").replace("/", "_");
        Path clientPath = testStoragePath.resolve(IceStorageConstants.DIR_CLIENTS)
                .resolve(String.valueOf(TEST_APP))
                .resolve(safeAddress + IceStorageConstants.SUFFIX_JSON);
        assertTrue(Files.exists(clientPath));

        // 销毁客户端
        client.destroy();

        // 验证客户端已销毁
        assertTrue(client.isDestroy());

        // 验证客户端信息文件已删除
        assertFalse(Files.exists(clientPath));
    }

    @Test
    @Order(3)
    void testConfigLoading() throws Exception {
        IceFileClient client = new IceFileClient(
                TEST_APP,
                testStoragePath.toString(),
                -1,
                Collections.emptySet(),
                5,
                10
        );

        client.start();

        // 验证版本已加载
        assertEquals(1, client.getLoadedVersion());

        client.destroy();
    }

    @Test
    @Order(4)
    void testIncrementalUpdate() throws Exception {
        IceFileClient client = new IceFileClient(
                TEST_APP,
                testStoragePath.toString(),
                -1,
                Collections.emptySet(),
                1, // 1秒轮询
                10
        );

        client.start();
        assertEquals(1, client.getLoadedVersion());

        // 创建版本2的更新
        Path appPath = testStoragePath.resolve(String.valueOf(TEST_APP));

        IceConfDto newConf = new IceConfDto();
        newConf.setId(2L);
        newConf.setApp(TEST_APP);
        newConf.setName("new-conf");
        newConf.setType((byte) 4); // LEAF_FLOW
        newConf.setStatus(IceStorageConstants.STATUS_ONLINE);
        newConf.setDebug((byte) 1);
        newConf.setTimeType((byte) 1);
        newConf.setInverse(false);
        newConf.setCreateAt(System.currentTimeMillis());
        newConf.setUpdateAt(System.currentTimeMillis());

        // 保存新conf
        Path confPath = appPath.resolve(IceStorageConstants.DIR_CONFS).resolve("2.json");
        Files.write(confPath, JacksonUtils.toJsonString(newConf).getBytes(StandardCharsets.UTF_8));

        // 创建版本更新文件
        String updateJson = "{\"version\":2,\"insertOrUpdateConfs\":[" + JacksonUtils.toJsonString(newConf) + "]}";
        Path updatePath = appPath.resolve(IceStorageConstants.DIR_VERSIONS).resolve("2_upd.json");
        Files.write(updatePath, updateJson.getBytes(StandardCharsets.UTF_8));

        // 更新版本
        Files.write(appPath.resolve(IceStorageConstants.FILE_VERSION), "2".getBytes(StandardCharsets.UTF_8));

        // 等待轮询检测到更新
        Thread.sleep(2000);

        // 验证版本已更新
        assertEquals(2, client.getLoadedVersion());

        client.destroy();
    }
}


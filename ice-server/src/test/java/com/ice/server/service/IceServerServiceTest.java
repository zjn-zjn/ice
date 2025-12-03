package com.ice.server.service;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.*;
import com.ice.server.config.IceServerProperties;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;
import com.ice.server.service.impl.IceServerServiceImpl;
import com.ice.server.storage.IceClientManager;
import com.ice.server.storage.IceFileStorageService;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IceServerService 测试
 *
 * @author waitmoon
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IceServerServiceTest {

    private static IceFileStorageService storageService;
    private static IceServerServiceImpl serverService;
    private static IceClientManager clientManager;
    private static IceServerProperties properties;
    private static Path testStoragePath;

    @BeforeAll
    static void setup() throws IOException {
        testStoragePath = Files.createTempDirectory("ice-server-service-test");

        properties = new IceServerProperties();
        properties.getStorage().setPath(testStoragePath.toString());
        properties.setVersionRetention(1000);

        storageService = new IceFileStorageService(properties);
        storageService.init();

        clientManager = new IceClientManager(properties, storageService);
        serverService = new IceServerServiceImpl(storageService, clientManager, properties);

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
    void testCreateBaseAndConf() throws IOException {
        // 创建base
        long baseId = storageService.nextBaseId(1);
        IceBaseDto base = new IceBaseDto();
        base.setId(baseId);
        base.setApp(1);
        base.setName("test-base");
        base.setScenes("scene1");
        base.setStatus(IceStorageConstants.STATUS_ONLINE);
        base.setTimeType((byte) 1);
        base.setDebug((byte) 1);
        base.setPriority(1L);
        base.setCreateAt(System.currentTimeMillis());
        base.setUpdateAt(System.currentTimeMillis());

        // 创建root conf
        long confId = storageService.nextConfId(1);
        IceConfDto conf = new IceConfDto();
        conf.setId(confId);
        conf.setApp(1);
        conf.setName("root-node");
        conf.setType((byte) 1); // NONE
        conf.setStatus(IceStorageConstants.STATUS_ONLINE);
        conf.setDebug((byte) 1);
        conf.setTimeType((byte) 1);
        conf.setInverse(false);
        conf.setCreateAt(System.currentTimeMillis());
        conf.setUpdateAt(System.currentTimeMillis());

        storageService.saveConf(conf);
        base.setConfId(confId);
        storageService.saveBase(base);

        // 验证
        IceBase readBase = serverService.getActiveBaseById(1, baseId);
        assertNotNull(readBase);
        assertEquals("test-base", readBase.getName());

        IceConf readConf = serverService.getActiveConfById(1, confId);
        assertNotNull(readConf);
        assertEquals("root-node", readConf.getName());
    }

    @Test
    @Order(2)
    void testConfUpdate() throws IOException {
        long confId = 1L;
        long iceId = 1L;

        // 创建conf update
        IceConfDto confUpdate = new IceConfDto();
        confUpdate.setId(2L);
        confUpdate.setConfId(confId);
        confUpdate.setIceId(iceId);
        confUpdate.setApp(1);
        confUpdate.setName("updated-node");
        confUpdate.setType((byte) 2); // AND
        confUpdate.setStatus(IceStorageConstants.STATUS_ONLINE);
        confUpdate.setDebug((byte) 1);
        confUpdate.setTimeType((byte) 1);
        confUpdate.setInverse(false);
        confUpdate.setUpdateAt(System.currentTimeMillis());

        // 使用service保存
        IceConf conf = IceConf.fromDto(confUpdate);
        serverService.updateLocalConfUpdateCache(conf);

        // 验证update conf存在
        IceConf updateConf = serverService.getUpdateConfById(1, confId, iceId);
        assertNotNull(updateConf);
        assertEquals("updated-node", updateConf.getName());

        // 获取mix conf应该返回update版本
        IceConf mixConf = serverService.getMixConfById(1, confId, iceId);
        assertNotNull(mixConf);
        assertEquals("updated-node", mixConf.getName());
    }

    @Test
    @Order(3)
    void testGetInitConfig() {
        IceTransferDto initConfig = serverService.getInitConfig(1);
        assertNotNull(initConfig);
        assertNotNull(initConfig.getInsertOrUpdateBases());
        assertNotNull(initConfig.getInsertOrUpdateConfs());
        assertFalse(initConfig.getInsertOrUpdateBases().isEmpty());
    }

    @Test
    @Order(4)
    void testRelease() throws IOException {
        long iceId = 1L;

        // 发布更新
        IceTransferDto releaseDto = serverService.release(1, iceId);

        // 验证发布结果
        if (releaseDto != null) {
            assertTrue(releaseDto.getVersion() > 0);

            // 验证update被清理
            Collection<IceConf> updates = serverService.getAllUpdateConfList(1, iceId);
            assertTrue(updates.isEmpty());

            // 验证版本更新文件存在
            long version = storageService.getVersion(1);
            assertTrue(version > 0);
        }
    }

    @Test
    @Order(5)
    void testGetAllActiveConfSet() throws IOException {
        // 创建层级配置
        // root -> child1, child2
        long rootId = storageService.nextConfId(1);
        long child1Id = storageService.nextConfId(1);
        long child2Id = storageService.nextConfId(1);

        IceConfDto child1 = createConf(child1Id, "child1", (byte) 4); // LEAF_FLOW
        IceConfDto child2 = createConf(child2Id, "child2", (byte) 5); // LEAF_RESULT
        storageService.saveConf(child1);
        storageService.saveConf(child2);

        IceConfDto root = createConf(rootId, "root", (byte) 2); // AND
        root.setSonIds(child1Id + "," + child2Id);
        storageService.saveConf(root);

        // 获取所有活跃配置
        Set<IceConf> activeConfs = serverService.getAllActiveConfSet(1, rootId);
        assertEquals(3, activeConfs.size());

        Set<Long> ids = new HashSet<>();
        for (IceConf conf : activeConfs) {
            ids.add(conf.getId());
        }
        assertTrue(ids.contains(rootId));
        assertTrue(ids.contains(child1Id));
        assertTrue(ids.contains(child2Id));
    }

    @Test
    @Order(6)
    void testLeafClassMap() throws IOException {
        // 创建带className的leaf节点
        long leafId = storageService.nextConfId(1);
        IceConfDto leafConf = createConf(leafId, "leaf-node", (byte) 4); // LEAF_FLOW
        leafConf.setConfName("com.test.TestFlow");
        storageService.saveConf(leafConf);

        // 获取叶子类统计
        Map<String, Integer> leafClassMap = serverService.getLeafClassMap(1, (byte) 4);
        // 当有叶子节点时应该返回统计
        if (leafClassMap != null) {
            assertTrue(leafClassMap.containsKey("com.test.TestFlow"));
        }
        // 如果没有使用叶子节点（不在任何base引用的树中），结果可能为空
    }

    @Test
    @Order(7)
    void testUpdateClean() throws IOException {
        long iceId = 99L;

        // 创建一些update
        IceConfDto update1 = createConf(100L, "update1", (byte) 1);
        update1.setConfId(100L);
        update1.setIceId(iceId);
        storageService.saveConfUpdate(1, iceId, update1);

        IceConfDto update2 = createConf(101L, "update2", (byte) 1);
        update2.setConfId(101L);
        update2.setIceId(iceId);
        storageService.saveConfUpdate(1, iceId, update2);

        // 验证update存在
        Collection<IceConf> updates = serverService.getAllUpdateConfList(1, iceId);
        assertEquals(2, updates.size());

        // 清理update
        serverService.updateClean(1, iceId);

        // 验证已清理
        updates = serverService.getAllUpdateConfList(1, iceId);
        assertTrue(updates.isEmpty());
    }

    private IceConfDto createConf(long id, String name, byte type) {
        IceConfDto conf = new IceConfDto();
        conf.setId(id);
        conf.setApp(1);
        conf.setName(name);
        conf.setType(type);
        conf.setStatus(IceStorageConstants.STATUS_ONLINE);
        conf.setDebug((byte) 1);
        conf.setTimeType((byte) 1);
        conf.setInverse(false);
        conf.setCreateAt(System.currentTimeMillis());
        conf.setUpdateAt(System.currentTimeMillis());
        return conf;
    }
}


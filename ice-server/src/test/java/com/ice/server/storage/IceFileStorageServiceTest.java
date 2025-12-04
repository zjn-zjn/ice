package com.ice.server.storage;

import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.*;
import com.ice.server.config.IceServerProperties;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文件系统存储服务测试
 *
 * @author waitmoon
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IceFileStorageServiceTest {

    private static IceFileStorageService storageService;
    private static Path testStoragePath;

    @BeforeAll
    static void setup() throws IOException {
        testStoragePath = Files.createTempDirectory("ice-test-storage");

        IceServerProperties properties = new IceServerProperties();
        properties.getStorage().setPath(testStoragePath.toString());

        storageService = new IceFileStorageService(properties);
        storageService.init();
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
    void testAppCRUD() throws IOException {
        // Create
        int appId = storageService.nextAppId();
        assertEquals(1, appId);

        IceAppDto app = new IceAppDto();
        app.setId(appId);
        app.setName("test-app");
        app.setInfo("Test application");
        app.setStatus(IceStorageConstants.STATUS_ONLINE);
        app.setCreateAt(System.currentTimeMillis());
        app.setUpdateAt(System.currentTimeMillis());
        storageService.saveApp(app);

        // Read
        IceAppDto readApp = storageService.getApp(appId);
        assertNotNull(readApp);
        assertEquals("test-app", readApp.getName());

        // List
        List<IceAppDto> apps = storageService.listApps();
        assertEquals(1, apps.size());

        // Update
        app.setName("updated-app");
        app.setUpdateAt(System.currentTimeMillis());
        storageService.saveApp(app);

        readApp = storageService.getApp(appId);
        assertEquals("updated-app", readApp.getName());
    }

    @Test
    @Order(2)
    void testBaseCRUD() throws IOException {
        int app = 1;

        // Create
        long baseId = storageService.nextBaseId(app);
        assertEquals(1, baseId);

        IceBaseDto base = new IceBaseDto();
        base.setId(baseId);
        base.setApp(app);
        base.setName("test-base");
        base.setScenes("scene1,scene2");
        base.setConfId(1L);
        base.setStatus(IceStorageConstants.STATUS_ONLINE);
        base.setTimeType((byte) 1);
        base.setDebug((byte) 1);
        base.setPriority(1L);
        base.setCreateAt(System.currentTimeMillis());
        base.setUpdateAt(System.currentTimeMillis());
        storageService.saveBase(base);

        // Read
        IceBaseDto readBase = storageService.getBase(app, baseId);
        assertNotNull(readBase);
        assertEquals("test-base", readBase.getName());

        // List
        List<IceBaseDto> bases = storageService.listBases(app);
        assertEquals(1, bases.size());

        // List active
        List<IceBaseDto> activeBases = storageService.listActiveBases(app);
        assertEquals(1, activeBases.size());

        // Soft delete
        storageService.deleteBase(app, baseId, false);
        readBase = storageService.getBase(app, baseId);
        assertEquals(IceStorageConstants.STATUS_DELETED, readBase.getStatus());
    }

    @Test
    @Order(3)
    void testConfCRUD() throws IOException {
        int app = 1;

        // Create
        long confId = storageService.nextConfId(app);
        assertEquals(1, confId);

        IceConfDto conf = new IceConfDto();
        conf.setId(confId);
        conf.setApp(app);
        conf.setName("test-conf");
        conf.setType((byte) 1);
        conf.setStatus(IceStorageConstants.STATUS_ONLINE);
        conf.setDebug((byte) 1);
        conf.setTimeType((byte) 1);
        conf.setInverse(false);
        conf.setCreateAt(System.currentTimeMillis());
        conf.setUpdateAt(System.currentTimeMillis());
        storageService.saveConf(conf);

        // Read
        IceConfDto readConf = storageService.getConf(app, confId);
        assertNotNull(readConf);
        assertEquals("test-conf", readConf.getName());

        // List
        List<IceConfDto> confs = storageService.listConfs(app);
        assertEquals(1, confs.size());
    }

    @Test
    @Order(4)
    void testConfUpdate() throws IOException {
        int app = 1;
        long iceId = 1;
        long confId = 2;

        // Create update
        IceConfDto confUpdate = new IceConfDto();
        confUpdate.setId(confId);
        confUpdate.setConfId(confId);
        confUpdate.setIceId(iceId);
        confUpdate.setApp(app);
        confUpdate.setName("test-conf-update");
        confUpdate.setType((byte) 2);
        confUpdate.setStatus(IceStorageConstants.STATUS_ONLINE);
        confUpdate.setUpdateAt(System.currentTimeMillis());
        storageService.saveConfUpdate(app, iceId, confUpdate);

        // Read
        IceConfDto readUpdate = storageService.getConfUpdate(app, iceId, confId);
        assertNotNull(readUpdate);
        assertEquals("test-conf-update", readUpdate.getName());

        // List
        List<IceConfDto> updates = storageService.listConfUpdates(app, iceId);
        assertEquals(1, updates.size());

        // Delete
        storageService.deleteConfUpdate(app, iceId, confId);
        readUpdate = storageService.getConfUpdate(app, iceId, confId);
        assertNull(readUpdate);
    }

    @Test
    @Order(5)
    void testPushHistory() throws IOException {
        int app = 1;
        long iceId = 1;

        // Create
        long pushId = storageService.nextPushId(app);
        assertEquals(1, pushId);

        IcePushHistoryDto history = new IcePushHistoryDto();
        history.setId(pushId);
        history.setApp(app);
        history.setIceId(iceId);
        history.setReason("test push");
        history.setOperator("tester");
        history.setPushData("{\"test\":\"data\"}");
        history.setCreateAt(System.currentTimeMillis());
        storageService.savePushHistory(history);

        // Read
        IcePushHistoryDto readHistory = storageService.getPushHistory(app, pushId);
        assertNotNull(readHistory);
        assertEquals("test push", readHistory.getReason());

        // List
        List<IcePushHistoryDto> histories = storageService.listPushHistories(app, iceId);
        assertEquals(1, histories.size());

        // Delete
        storageService.deletePushHistory(app, pushId);
        readHistory = storageService.getPushHistory(app, pushId);
        assertNull(readHistory);
    }

    @Test
    @Order(6)
    void testVersion() throws IOException {
        int app = 1;

        // Initial version
        long version = storageService.getVersion(app);
        assertEquals(0, version);

        // Set version
        storageService.setVersion(app, 1);
        version = storageService.getVersion(app);
        assertEquals(1, version);

        // Save version update
        IceTransferDto updateDto = new IceTransferDto();
        updateDto.setVersion(1L);
        storageService.saveVersionUpdate(app, 1, updateDto);

        // Increment version
        storageService.setVersion(app, 2);
        version = storageService.getVersion(app);
        assertEquals(2, version);
    }

    @Test
    @Order(7)
    void testClientInfo() throws IOException {
        int app = 1;

        // Save client
        IceClientInfo client = new IceClientInfo();
        client.setApp(app);
        client.setAddress("localhost_8080_12345");
        client.setLastHeartbeat(System.currentTimeMillis());
        client.setStartTime(System.currentTimeMillis());
        client.setLoadedVersion(1L);
        storageService.saveClient(client);

        // List clients
        List<IceClientInfo> clients = storageService.listClients(app);
        assertEquals(1, clients.size());
        assertEquals("localhost_8080_12345", clients.get(0).getAddress());

        // Delete client
        storageService.deleteClient(app, client.getAddress());
        clients = storageService.listClients(app);
        assertEquals(0, clients.size());
    }

    @Test
    @Order(8)
    void testIdGeneratorConcurrency() throws IOException {
        int app = 1;

        // Generate multiple IDs
        long id1 = storageService.nextConfId(app);
        long id2 = storageService.nextConfId(app);
        long id3 = storageService.nextConfId(app);

        // IDs should be sequential (starting from 2 since we already created one in testConfCRUD)
        assertTrue(id2 > id1);
        assertTrue(id3 > id2);
    }
}


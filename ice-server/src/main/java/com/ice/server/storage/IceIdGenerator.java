package com.ice.server.storage;

import com.ice.common.constant.IceStorageConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * ID生成器
 * 基于文件系统的递增ID生成
 *
 * @author waitmoon
 */
@Slf4j
public class IceIdGenerator {

    private final Path idFilePath;
    private final Object lock = new Object();

    public IceIdGenerator(Path idFilePath) {
        this.idFilePath = idFilePath;
    }

    /**
     * 生成下一个ID
     *
     * @return 新的ID
     */
    public long nextId() throws IOException {
        synchronized (lock) {
            long currentId = 0;
            if (Files.exists(idFilePath)) {
                String content = new String(Files.readAllBytes(idFilePath), StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    currentId = Long.parseLong(content);
                }
            } else {
                Files.createDirectories(idFilePath.getParent());
            }

            long nextId = currentId + 1;

            // 使用临时文件+rename确保原子性
            Path tmpPath = idFilePath.resolveSibling(idFilePath.getFileName() + IceStorageConstants.SUFFIX_TMP);
            Files.write(tmpPath, String.valueOf(nextId).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmpPath, idFilePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            return nextId;
        }
    }

    /**
     * 获取当前ID(不递增)
     *
     * @return 当前ID，如果文件不存在返回0
     */
    public long currentId() throws IOException {
        synchronized (lock) {
            if (Files.exists(idFilePath)) {
                String content = new String(Files.readAllBytes(idFilePath), StandardCharsets.UTF_8).trim();
                if (!content.isEmpty()) {
                    return Long.parseLong(content);
                }
            }
            return 0;
        }
    }
}


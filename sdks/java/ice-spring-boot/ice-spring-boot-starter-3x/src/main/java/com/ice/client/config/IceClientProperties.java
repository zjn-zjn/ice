package com.ice.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * @author waitmoon
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ice")
public class IceClientProperties {
    /**
     * appId
     */
    private Integer app;

    /**
     * 文件系统存储配置
     */
    private IceStorageProperties storage = new IceStorageProperties();

    /**
     * node scan packages
     * multiple packages split with ','
     * default main package
     */
    private Set<String> scan;

    /**
     * ice thread pool
     */
    private IceClientThreadPoolProperties pool = new IceClientThreadPoolProperties();

    /**
     * version轮询间隔(秒)
     */
    private int pollInterval = 5;

    /**
     * 心跳更新间隔(秒)
     */
    private int heartbeatInterval = 10;

    @Data
    public static class IceClientThreadPoolProperties {
        private int parallelism = -1;
    }

    @Data
    public static class IceStorageProperties {
        /**
         * 文件系统存储路径
         */
        private String path;
    }
}

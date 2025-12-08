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
     * File system storage configuration.
     */
    private IceStorageProperties storage = new IceStorageProperties();

    /**
     * Node scan packages.
     * Multiple packages split with ','.
     * Default is main package.
     */
    private Set<String> scan;

    /**
     * Ice thread pool configuration.
     */
    private IceClientThreadPoolProperties pool = new IceClientThreadPoolProperties();

    /**
     * Version polling interval in seconds.
     */
    private int pollInterval = 5;

    /**
     * Heartbeat update interval in seconds.
     */
    private int heartbeatInterval = 10;

    @Data
    public static class IceClientThreadPoolProperties {
        private int parallelism = -1;
    }

    @Data
    public static class IceStorageProperties {
        /**
         * File system storage path.
         */
        private String path;
    }
}

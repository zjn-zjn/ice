package com.ice.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;

/**
 * @author waitmoon
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ice")
public class IceClientProperties {
    /*
     * appId
     */
    private Integer app;
    /*
     * server serverHost:serverPort
     */
    private String server;
    /*
     * default 16M, size bigger than this may dirty data
     */
    private int maxFrameLength = 16 * 1024 * 1024;
    /*
     * node scan packages
     * multiple packages split with ','
     * default main package
     */
    private Set<String> scan;
    /*
     * ice thread pool
     */
    private IceClientThreadPoolProperties pool = new IceClientThreadPoolProperties();

    @Data
    public static class IceClientThreadPoolProperties {
        private int parallelism = -1;
    }
}

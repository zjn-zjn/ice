package com.ice.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
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
     * ice thread pool
     */
    private IceClientThreadPoolProperties pool = new IceClientThreadPoolProperties();

    @Data
    public static class IceClientThreadPoolProperties {
        private int parallelism = -1;
    }
}

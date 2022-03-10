package com.ice.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ice")
public class IceServerProperties {
    /*
     * rmi config
     */
    private IceServerRmiProperties rmi = new IceServerRmiProperties();
    /*
     * ice thread pool
     */
    private IceServerThreadPoolProperties pool = new IceServerThreadPoolProperties();

    @Data
    public static class IceServerRmiProperties {
        private int port = 8212;
    }

    @Data
    public static class IceServerThreadPoolProperties {
        private int coreSize = 4;
        private int maxSize = 4;
        private int keepAliveSeconds = 60;
        private int queueCapacity = 60000;
    }
}

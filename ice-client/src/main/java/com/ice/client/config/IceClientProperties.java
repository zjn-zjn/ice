package com.ice.client.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ice")
public class IceClientProperties {
    /*
     * appId
     */
    private Integer app;
    /*
     * rabbitMq config
     */
    private IceClientRabbitProperties rabbit = new IceClientRabbitProperties();
    /*
     * ice thread pool
     */
    private IceClientThreadPoolProperties pool = new IceClientThreadPoolProperties();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IceClientRabbitProperties {
        private int port;
        private String host;
        private String username;
        private String password;
        private int replyTimeout = 10000;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IceClientThreadPoolProperties {
        private int coreSize = 4;
        private int maxSize = 4;
        private int keepAliveSeconds = 10;
        private int queueCapacity = 60000;
    }
}

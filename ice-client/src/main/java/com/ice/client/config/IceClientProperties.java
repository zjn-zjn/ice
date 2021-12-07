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
     * rabbitMq配置
     */
    private IceClientRabbitProperties rabbit = new IceClientRabbitProperties();

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
}

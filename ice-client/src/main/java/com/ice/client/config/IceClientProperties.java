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
     * 等待初始化返回时间 默认10s
     */
    private int initTimeOut = 10000;
    /*
     * rabbitMq配置
     */
    private IceClientRabbitProperties rabbit = new IceClientRabbitProperties();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IceClientRabbitProperties{
        private int port;
        private String host;
        private String username;
        private String password;
    }
}

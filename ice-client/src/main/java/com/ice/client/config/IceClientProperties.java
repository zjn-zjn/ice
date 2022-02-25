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
     * rmi config
     */
    private IceClientRmiProperties rmi = new IceClientRmiProperties();
    /*
     * ice thread pool
     */
    private IceClientThreadPoolProperties pool = new IceClientThreadPoolProperties();

    @Data
    public static class IceClientRmiProperties {
        private int port = 8210;
        private int communicatePort = 0;
        private String server;
        private String serverHost;
        private int serverPort;

        public void setServer(String server) {
            this.server = server;
            String[] serverHostPort = server.split(":");
            try {
                this.serverHost = serverHostPort[0];
                this.serverPort = Integer.parseInt(serverHostPort[1]);
            } catch (Exception e) {
                throw new RuntimeException("ice server config error conf:" + server);
            }
        }
    }

    @Data
    public static class IceClientThreadPoolProperties {
        private int coreSize = 4;
        private int maxSize = 4;
        private int keepAliveSeconds = 60;
        private int queueCapacity = 60000;
    }
}

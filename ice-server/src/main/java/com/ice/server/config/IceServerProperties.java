package com.ice.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ice")
public class IceServerProperties {

    private String ip = "0.0.0.0";
    //ice nio port
    private int port = 18121;
    //if there is no read request for readerIdleTime, close the client
    private int readerIdleTime;
    //default 16M, size bigger than this may dirty data
    private int maxFrameLength = 16 * 1024 * 1024;
    //timeout for client response
    private int clientRspTimeOut = 3000;
    //ice thread pool
    private IceServerThreadPoolProperties pool = new IceServerThreadPoolProperties();

    @Data
    public static class IceServerThreadPoolProperties {
        private int coreSize = 4;
        private int maxSize = 4;
        private int keepAliveSeconds = 60;
        private int queueCapacity = 60000;
    }
}

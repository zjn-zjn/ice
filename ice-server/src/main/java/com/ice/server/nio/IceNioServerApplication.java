package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.service.IceServerService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;

/**
 * @author waitmoon
 */
@SpringBootApplication
public class IceNioServerApplication implements ApplicationRunner {


    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        IceNioServer iceNioServer = new IceNioServer(properties, serverService);
        Runtime.getRuntime().addShutdownHook(new Thread(iceNioServer::destroy));
        iceNioServer.run();
    }
}
 
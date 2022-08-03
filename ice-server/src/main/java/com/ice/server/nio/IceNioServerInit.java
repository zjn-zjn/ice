package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.service.IceServerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author waitmoon
 */
@Component
public class IceNioServerInit implements CommandLineRunner {

    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    @Override
    public void run(String... args) throws Exception {
        IceNioServer iceNioServer = new IceNioServer(properties, serverService);
        Runtime.getRuntime().addShutdownHook(new Thread(iceNioServer::destroy));
        iceNioServer.run();
    }
}
 
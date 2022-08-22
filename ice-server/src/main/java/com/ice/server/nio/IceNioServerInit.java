package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.nio.ha.IceNioServerHa;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author waitmoon
 */
@Slf4j
@Component
public class IceNioServerInit implements CommandLineRunner, DisposableBean {

    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    @Autowired(required = false)
    private IceNioServerHa serverHa;

    private IceNioServer iceNioServer;

    public static volatile boolean ready = false;

    @Override
    public void destroy() throws Exception {
        if (iceNioServer != null) {
            iceNioServer.destroy();
        }
    }

    @Override
    public void run(String... args) throws Exception {
        serverService.refresh();
        iceNioServer = new IceNioServer(properties, serverService, serverHa);
        try {
            iceNioServer.start();
        } catch (Throwable t) {
            iceNioServer.destroy();
            throw new RuntimeException("ice nio server start error", t);
        }
        ready = true;
    }
}
 
package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.nio.ha.IceNioServerHa;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author waitmoon
 */
@Component
@Slf4j
public class IceNioServerInit implements InitializingBean, DisposableBean {

    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    @Autowired(required = false)
    private IceNioServerHa nioServerZk;

    private IceNioServer iceNioServer;

    @Override
    public void afterPropertiesSet() throws Exception {
        iceNioServer = new IceNioServer(properties, serverService, nioServerZk);
        try {
            iceNioServer.start();
        } catch (Throwable t) {
            iceNioServer.destroy();
            throw new RuntimeException("ice nio server start error", t);
        }
    }

    @Override
    public void destroy() throws Exception {
        if (iceNioServer != null) {
            iceNioServer.destroy();
        }
    }
}
 
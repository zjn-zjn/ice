package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import com.ice.server.service.IceServerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;

@SpringBootApplication
public class IceNioServerApplication implements CommandLineRunner {


    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    public static void main(String[] args) {
        SpringApplication.run(IceNioServerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        IceNioServer iceNioServer = new IceNioServer(properties, serverService);
        Runtime.getRuntime().addShutdownHook(new Thread(iceNioServer::destroy));
        iceNioServer.run();
    }
}
 
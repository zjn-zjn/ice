package com.ice.server.nio;

import com.ice.server.config.IceServerProperties;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;

@SpringBootApplication
public class IceNioServerApplication implements CommandLineRunner {


    @Resource
    private IceServerProperties properties;

    public static void main(String[] args) {
        SpringApplication.run(IceNioServerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        IceNioServer iceNioServer = new IceNioServer(properties);
        Runtime.getRuntime().addShutdownHook(new Thread(iceNioServer::destroy));
        iceNioServer.run();
    }
}
 
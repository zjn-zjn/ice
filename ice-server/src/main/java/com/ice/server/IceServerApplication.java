package com.ice.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author waitmoon
 */
@SpringBootApplication
@EnableScheduling
public class IceServerApplication {
    public static void main(String... args) {
        SpringApplication.run(IceServerApplication.class, args);
    }
}
package com.ice.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author zjn
 */
@SpringBootApplication
@EnableScheduling
@EnableRabbit
@MapperScan(basePackages = "com.ice.server.dao")
@EnableTransactionManagement
public class IceServerApplication {
  public static void main(String... args) {
    SpringApplication.run(IceServerApplication.class, args);
  }
}
package com.ice.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author zjn
 */
@SpringBootApplication
@ComponentScan({ "com.ice.client", "com.ice.test" })
public class IceTestApplication {
  public static void main(String... args) {
    SpringApplication.run(IceTestApplication.class, args);
  }
}

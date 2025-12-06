package com.ice.test.config;


import com.ice.test.service.SendService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SendConfig {

    @Bean
    public SendService sendService2() {
        return new SendService();
    }
}

package com.ice.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author waitmoon
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IceServerProperties.class)
public class ServerConfig {

    @Bean
    public WebServerFactoryCustomizer<ConfigurableWebServerFactory> webServerFactoryCustomizer() {
        return factory -> {
            ErrorPage errorPage = new ErrorPage(HttpStatus.NOT_FOUND, "/");
            factory.addErrorPages(errorPage);
        };
    }
}
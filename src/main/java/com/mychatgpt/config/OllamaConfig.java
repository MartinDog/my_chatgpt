package com.mychatgpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ollama")
@Data
public class OllamaConfig {
    private String host;
    private int port;
    private String model;

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
}

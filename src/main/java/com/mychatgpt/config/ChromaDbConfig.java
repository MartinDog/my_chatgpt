package com.mychatgpt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "chromadb")
@Data
public class ChromaDbConfig {
    private String host;
    private int port;
    private String collectionName;

    public String getBaseUrl() {
        return "http://" + host + ":" + port;
    }
}

package com.mychatgpt.vectordb;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResult {
    private String id;
    private String document;
    private double distance;
    private Map<String, String> metadata;
}

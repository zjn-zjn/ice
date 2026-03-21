package com.ice.common.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MockResult {
    private String mockId;
    private boolean success;
    private Map<String, Object> roam;
    private String trace;
    private long ts;
    private String process;
    private String error;
    private long executeAt;
}

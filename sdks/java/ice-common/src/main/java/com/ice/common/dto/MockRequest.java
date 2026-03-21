package com.ice.common.dto;

import lombok.Data;

import java.util.Map;

@Data
public class MockRequest {
    private String mockId;
    private int app;
    private long iceId;
    private long confId;
    private String scene;
    private long ts;
    private byte debug;
    private Map<String, Object> roam;
    private long createAt;
}

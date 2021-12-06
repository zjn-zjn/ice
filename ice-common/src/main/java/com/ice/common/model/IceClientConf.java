package com.ice.common.model;

import lombok.Data;

@Data
public class IceClientConf {
    private String ip;
    private long iceId;
    private int app;
    private IceClientHandler handler;
}

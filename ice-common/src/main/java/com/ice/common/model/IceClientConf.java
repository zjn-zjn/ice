package com.ice.common.model;

import lombok.Data;

@Data
public class IceClientConf {
    private String ip;
    private long confId;
    private int app;
    private IceClientNode node;
}

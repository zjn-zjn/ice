package com.ice.common.model;

import lombok.Data;

@Data
public class IceShowConf {
    private String ip;
    private long confId;
    private int app;
    private IceShowNode node;
}

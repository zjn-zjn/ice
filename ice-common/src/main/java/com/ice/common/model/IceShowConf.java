package com.ice.common.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Set;

@Data
public class IceShowConf implements Serializable {
    private String address;
    private long confId;
    private int app;
    private long iceId;
    private Set<String> registerClients;
    private IceShowNode root;
}

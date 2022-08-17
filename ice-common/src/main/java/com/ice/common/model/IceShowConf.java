package com.ice.common.model;

import lombok.Data;

import java.util.Set;

/**
 * @author waitmoon
 */
@Data
public class IceShowConf {
    private String address;
    private long confId;
    private int app;
    private long iceId;
    private Set<String> registerClients;
    private IceShowNode root;
}

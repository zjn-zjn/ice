package com.ice.common.model;

import lombok.Data;

import java.util.List;
import java.util.Map;
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
    @Deprecated
    private Set<String> registerClients;
    private IceShowNode root;
    private Integer updateCount;
    private ClientRegistryInfo clientRegistry;
    private Map<Byte, List<LeafNodeInfo>> leafClassMap;

    @Data
    public static class ClientRegistryInfo {
        private List<ClientInfo> mainClients;
        private Map<String, List<ClientInfo>> laneClients;
    }

    @Data
    public static class ClientInfo {
        private String address;
        private Set<String> classes;
    }
}

package com.ice.core.client.ha;

/**
 * @author waitmoon
 * ice nio server discovery
 */
public interface IceServerHaDiscovery {

    boolean support();

    String initServerLeaderAddress() throws Exception;

    String getServerLeaderAddress();

    void destroy();
}

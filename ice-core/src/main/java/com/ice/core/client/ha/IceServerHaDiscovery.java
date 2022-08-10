package com.ice.core.client.ha;

/**
 * @author waitmoon
 * ice nio server discovery
 */
public interface IceServerHaDiscovery {
    /**
     * support or not
     * @return is support
     */
    boolean support();

    /**
     * init ha discovery
     * @param server address
     */
    void init(String server);

    /**
     * refresh to get current server leader address
     * @return current server leader
     * @throws Exception e
     */
    String refreshServerLeaderAddress() throws Exception;

    /**
     * get server leader address
     * @return server leader
     */
    String getServerLeaderAddress();

    /**
     * clean, close
     */
    void destroy();
}

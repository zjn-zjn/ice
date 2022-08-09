package com.ice.core.client.ha;

/**
 * @author waitmoon
 * standAlone not support ha
 */
public class IceServerStandAlone implements IceServerHaDiscovery {

    @Override
    public boolean support() {
        return false;
    }

    @Override
    public String initServerLeaderAddress() throws Exception {
        return null;
    }

    @Override
    public String getServerLeaderAddress() {
        return null;
    }

    @Override
    public void destroy() {
    }
}
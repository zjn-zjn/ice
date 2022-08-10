package com.ice.core.client.ha;

/**
 * @author waitmoon
 * standAlone
 */
public class IceServerStandAlone implements IceServerHaDiscovery {

    @Override
    public boolean support() {
        return false;
    }

    @Override
    public void init(String server) {
    }

    @Override
    public String refreshServerLeaderAddress() throws Exception {
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
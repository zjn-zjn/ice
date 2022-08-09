package com.ice.server.nio.ha;


import java.io.IOException;

public interface IceNioServerHa {

    void register() throws Exception;

    void destroy() throws IOException;

    boolean isLeader();

    String getLeaderWebAddress() throws Exception;
}

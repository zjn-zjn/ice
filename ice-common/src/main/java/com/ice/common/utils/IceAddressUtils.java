package com.ice.common.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

/**
 * @author zjn
 */
public final class IceAddressUtils {

    private static volatile String address;

    /*
     * address with port
     */
    public static String getAddress(SocketChannel sk) throws IOException {
        if (address != null) {
            return address;
        }
        synchronized (IceAddressUtils.class) {
            if (address != null) {
                return address;
            }
            if (sk == null) {
                throw new RuntimeException("SocketChannel empty");
            }
            InetSocketAddress localAddress = (InetSocketAddress) sk.getLocalAddress();
            String host = localAddress.getAddress().getHostAddress();
            if ("127.0.0.1".equals(host)) {
                host = InetAddress.getLocalHost().getHostName();
            }
            address = host + ":" + localAddress.getPort() + "/" + UUIDUtils.generateMost22UUID();
            return address;
        }
    }

    public static String getAddress() {
        if (address != null) {
            return address;
        }
        throw new RuntimeException("address not ready");
    }
}
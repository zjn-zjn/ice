package com.ice.client.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * @author zjn
 */
@Component("addressUtils")
public final class AddressUtils {

    private static volatile String address;
    private static volatile String host;
    private static String port;

    @Value("${server.port}")
    public void setPort(String port) {
        AddressUtils.port = port;
    }

    /*
     * address with port
     */
    public static String getAddress() {
        if (address != null) {
            return address;
        }
        synchronized (AddressUtils.class) {
            if (address != null) {
                return address;
            }
            address = getHost() + ":" + port;
            return address;
        }
    }

    public static String getHost() {
        if (host != null) {
            return host;
        }
        synchronized (AddressUtils.class) {
            if (host != null) {
                return host;
            }
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                throw new RuntimeException("get host failed", e);
            }
            return host;
        }
    }
}
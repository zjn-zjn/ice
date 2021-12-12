package com.ice.client.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/**
 * @author zjn
 */
@Component("addressUtils")
public final class AddressUtils {

    private static String address;
    private static String port;

    @Value("${server.port:}")
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
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                throw new RuntimeException("get host failed", e);
            }
            address = host + ":" + port;
            return address;
        }
    }
}
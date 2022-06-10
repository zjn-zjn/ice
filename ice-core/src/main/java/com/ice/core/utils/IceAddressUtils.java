package com.ice.core.utils;

import com.ice.common.utils.UUIDUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author zjn
 */
public final class IceAddressUtils {

    private static volatile String address;

    /*
     * unique address
     */
    public static String getAddress(Integer app) {
        if (address != null) {
            return address;
        }
        synchronized (IceAddressUtils.class) {
            if (address != null) {
                return address;
            }
            if (app == null) {
                throw new RuntimeException("app can not be null while init address");
            }
            String host = null;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
                if ("127.0.0.1".equals(host)) {
                    host = InetAddress.getLocalHost().getHostName();
                }
            } catch (UnknownHostException e) {
                //ignore
            }
            address = host == null ? (app + "/" + UUIDUtils.generateShortId()) : (host + "/" + app + "/" + UUIDUtils.generateShortId());
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
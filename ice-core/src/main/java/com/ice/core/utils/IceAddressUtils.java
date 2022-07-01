package com.ice.core.utils;

import com.ice.common.utils.UUIDUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author waitmoon
 */
public final class IceAddressUtils {

    /*
     * ice unique address
     */
    public static String getAddress(Integer app) {
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
        return host == null ? (app + "/" + UUIDUtils.generateShortId()) : (host + "/" + app + "/" + UUIDUtils.generateShortId());
    }
}
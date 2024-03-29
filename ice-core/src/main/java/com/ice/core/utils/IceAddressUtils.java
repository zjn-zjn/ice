package com.ice.core.utils;

import com.ice.common.utils.UUIDUtils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

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

    public static String getAddress() {
        String host = null;
        try {
            host = InetAddress.getLocalHost().getHostAddress();
            if ("127.0.0.1".equals(host) || "localhost".equals(host)) {
                host = getHostIp();
            }
        } catch (UnknownHostException e) {
            //ignore
        }
        return host;
    }

    public static String getHostIp() {
        String result = null;
        Enumeration<NetworkInterface> interfaces = null;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            // handle error
        }

        if (interfaces != null) {
            while (interfaces.hasMoreElements() && result == null) {
                NetworkInterface i = interfaces.nextElement();
                Enumeration<InetAddress> addresses = i.getInetAddresses();
                while (addresses.hasMoreElements() && (result == null || result.isEmpty())) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() &&
                            address.isSiteLocalAddress()) {
                        result = address.getHostAddress();
                    }
                }
            }
        }
        return result;
    }
}
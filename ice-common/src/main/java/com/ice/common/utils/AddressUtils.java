package com.ice.common.utils;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * @author zjn
 */
public final class AddressUtils {
    private AddressUtils() {
    }

    private static volatile String hostPort;

    /*
     * address with port
     */
    public static String getAddress() {
        if (hostPort != null) {
            return hostPort;
        }

        synchronized (AddressUtils.class) {
            if (hostPort != null) {
                return hostPort;
            }
            String host;
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                host = "";
            }
            Set<ObjectName> objectNames;
            try {
                MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
                objectNames = beanServer.queryNames(new ObjectName("*:type=Connector,*"),
                        Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
            } catch (Exception e) {
                objectNames = null;
            }
            String port = "";
            if (objectNames != null && !objectNames.isEmpty()) {
                for (ObjectName objectName : objectNames) {
                    port = objectName.getKeyProperty("port");
                    if (port != null && !port.isEmpty()) {
                        break;
                    }
                }
            }
            hostPort = host + ":" + port;
            return hostPort;
        }
    }
}
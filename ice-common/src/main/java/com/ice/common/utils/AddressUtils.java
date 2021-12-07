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

    /*
     * address with port
     */
    public static String getAddressPort() {

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
        return host + ":" + port;
    }

    /*
     * adress
     */
    public static String getAddressHost() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress();
    }

    /*
     * port
     */
    public static int getTomcatPort() throws MalformedObjectNameException {
        MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> objectNames = beanServer
                .queryNames(new ObjectName("*:type=Connector,*"), Query.match(Query.attr("protocol"), Query.value("HTTP/1.1")));
        String port = "-1";
        if (objectNames != null && !objectNames.isEmpty()) {
            for (ObjectName objectName : objectNames) {
                port = objectName.getKeyProperty("port");
                if (port != null && !port.isEmpty()) {
                    break;
                }
            }
        }
        return port == null ? -1 : Integer.parseInt(port);
    }
}
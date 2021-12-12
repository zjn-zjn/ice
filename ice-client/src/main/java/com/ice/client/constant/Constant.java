package com.ice.client.constant;

import com.ice.client.utils.AddressUtils;

/**
 * @author zjn
 * client constant
 */
public final class Constant {

    private Constant() {
    }

    public static String getUpdateExchange() {
        return "ice.update.exchange";
    }

    @Deprecated
    public static String getShowConfExchange() {
        return "ice.show.conf.exchange";
    }

    public static String getConfExchange() {
        return "ice.conf.exchange";
    }

    public static String getConfClazzCheckExchange() {
        return "ice.conf.clazz.check.exchange";
    }

    public static String getAllConfIdExchange() {
        return "ice.all.conf.id.exchange";
    }

    public static String getMockExchange() {
        return "ice.mock.exchange";
    }

    public static String getInitExchange() {
        return "ice.init.exchange";
    }

    public static String getUpdateRouteKey(Integer app) {
        return "ice.update." + app;
    }

    @Deprecated
    public static String getShowConfQueue(Integer app) {
        return "ice.show.conf." + app;
    }

    public static String getConfClazzCheckQueue(Integer app) {
        return "ice.conf.clazz.check." + app;
    }

    public static String getConfQueue(Integer app) {
        return "ice.conf." + app;
    }

    public static String getAllConfIdQueue(Integer app) {
        return "ice.all.conf.id." + app;
    }

    public static String getMockQueue(Integer app) {
        return "ice.mock." + app;
    }

    public static String genUpdateTmpQueue() {
        /*"ice.tmp.queue-" + host + ":" + port*/
        return "ice.tmp.queue-" + AddressUtils.getAddress();
    }
}

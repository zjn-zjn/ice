package com.ice.core.utils;

/**
 * @author zjn
 * IceBean tool class
 * init leaf node`s bean
 */
public final class IceBeanUtils {

    private static IceBeanFactory factory;

    private IceBeanUtils() {
    }

    public static void autowireBean(Object existingBean) {
        factory.autowireBean(existingBean);
    }

    public static boolean containsBean(String name) {
        if (factory == null) {
            return false;
        }
        return factory.containsBean(name);
    }

    public static Object getBean(String name) {
        if (factory == null) {
            return null;
        }
        return factory.getBean(name);
    }

    public static void setFactory(IceBeanFactory factory) {
        IceBeanUtils.factory = factory;
    }

    public interface IceBeanFactory {

        void autowireBean(Object existingBean);

        boolean containsBean(String name);

        Object getBean(String name);
    }
}

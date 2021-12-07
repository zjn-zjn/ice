package com.ice.core.utils;

/**
 * @author zjn
 * IceBean工具类
 * 组装叶子节点在初始化时使用的Bean
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
        /*
         * 注入Bean
         *
         * @param existingBean 待填充对象
         */
        void autowireBean(Object existingBean);

        /*
         * 检查是否有此Bean
         *
         * @param name beanName
         */
        boolean containsBean(String name);

        /*
         * 根据名称获取bean
         *
         * @param name beanName
         */
        Object getBean(String name);
    }
}

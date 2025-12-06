package com.ice.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author waitmoon
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface IceNode {

    String name() default "";

    String desc() default "";

    /**
     * 排序顺序，用于前端展示叶子类列表时排序
     * 值越小越靠前，默认100
     */
    int order() default 100;
}

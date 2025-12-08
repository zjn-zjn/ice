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

    /**
     * 类名别名，用于多语言兼容。
     * 当配置文件中的 confName 与此别名匹配时，会映射到当前 Java 类。
     * 例如: Go SDK 注册的 clazz 为 "score_flow"，Java 类可以设置 alias = "score_flow" 来兼容。
     * 支持多个别名，如: alias = {"score_flow", "ScoreFlow"}
     */
    String[] alias() default {};
}

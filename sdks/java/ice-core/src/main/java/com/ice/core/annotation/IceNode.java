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
     * Display order for leaf class list in UI.
     * Lower value appears first, default is 100.
     */
    int order() default 100;

    /**
     * Class name aliases for multi-language compatibility.
     * When confName in configuration matches an alias, it maps to this Java class.
     * Example: If Go SDK registers clazz as "score_flow", Java class can set alias = "score_flow" for compatibility.
     * Multiple aliases are supported, e.g., alias = {"score_flow", "ScoreFlow"}
     */
    String[] alias() default {};
}

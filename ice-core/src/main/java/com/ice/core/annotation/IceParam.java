package com.ice.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface IceParam {
  /**
   * 除以下value字段外指定从IceRoam拿value字段作为方法入参
   * "time":requestTime
   * "roam":IceRoam
   * "pack":IcePack
   * "cxt":IceContext
   *
   * @return paramName
   */
  String value();
}

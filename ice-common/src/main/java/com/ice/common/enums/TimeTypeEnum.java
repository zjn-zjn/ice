package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * 时间类型 用于校验
 * 备注:
 * 1.测试版无视Server对节点到期进行的上下线行为(测试版节点即使过期也会一直存在，不会下线)
 * 2.测试版仅不会下线,但真实时间校验不通过依然不会执行
 * 3.即使是测试版,时间配置有误的节点依旧不会上线
 */
public enum TimeTypeEnum {
/*
   * 无时间限制
   */
  NONE((byte) 1),
/*
   * 大于开始时间
   */
  AFTER_START((byte) 5),
/*
   * 小于结束时间
   */
  BEFORE_END((byte) 6),
/*
   * 在开始时间与结束时间之内
   */
  BETWEEN((byte) 7),
/*
   * 测试版大于开始时间
   */
  TEST_AFTER_START((byte) 5),
/*
   * 测试版小于结束时间
   */
  TEST_BEFORE_END((byte) 6),
/*
   * 测试版在开始时间与结束时间之内
   */
  TEST_BETWEEN((byte) 7);

  private static final Map<Byte, TimeTypeEnum> MAP = new HashMap<>();

  static {
    for (TimeTypeEnum enums : TimeTypeEnum.values()) {
      MAP.put(enums.getType(), enums);
    }
  }

  private final byte type;

  TimeTypeEnum(byte type) {
    this.type = type;
  }

  public static TimeTypeEnum getEnum(byte type) {
    return MAP.get(type);
  }

  public byte getType() {
    return type;
  }
}

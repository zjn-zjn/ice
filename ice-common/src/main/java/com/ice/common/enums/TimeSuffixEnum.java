package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * 后缀时间类型
 */
public enum TimeSuffixEnum {
  /**
   * 无后缀
   */
  NONE((byte) 1),
  /**
   * 秒
   */
  SECONDS((byte) 2),
  /**
   * 分钟
   */
  MINUTES((byte) 3),
  /**
   * 小时
   */
  HOURS((byte) 4),
  /**
   * 天
   */
  DAYS((byte) 5),
  /**
   * 周
   */
  WEEKS((byte) 6),
  /**
   * 月
   */
  MONTH((byte) 7),

  /**
   * 年
   */
  YEAR((byte) 8);

  private static final Map<Byte, TimeSuffixEnum> MAP = new HashMap<>();

  static {
    for (TimeSuffixEnum enums : TimeSuffixEnum.values()) {
      MAP.put(enums.getType(), enums);
    }
  }

  private final byte type;

  TimeSuffixEnum(byte type) {
    this.type = type;
  }

  public static TimeSuffixEnum getEnum(byte type) {
    return MAP.get(type);
  }

  public byte getType() {
    return type;
  }
}

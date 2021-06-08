package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * 叶子节点异常处理方案
 * 默认终止执行不落盘
 * 是否打印日志取决于节点debug配置
 */
public enum ErrorHandleEnum {
  /**
   * 继续执行做NONE处理
   */
  CONTINUE_NONE((byte) 0),

  /**
   * 继续执行做FALSE处理
   */
  CONTINUE_FALSE((byte) 1),

  /**
   * 继续执行做TRUE处理
   */
  CONTINUE_TRUE((byte) 2),

  /**
   * 终止执行不落盘
   */
  SHUT_DOWN((byte) 3),

  /**
   * 终止执行并落盘
   */
  SHUT_DOWN_STORE((byte) 4);

  private static final Map<Byte, ErrorHandleEnum> MAP = new HashMap<>();

  static {
    for (ErrorHandleEnum enums : ErrorHandleEnum.values()) {
      MAP.put(enums.getType(), enums);
    }
  }

  private final byte type;

  ErrorHandleEnum(byte type) {
    this.type = type;
  }

  public static ErrorHandleEnum getEnum(byte type) {
    return MAP.get(type);
  }

  public byte getType() {
    return type;
  }
}

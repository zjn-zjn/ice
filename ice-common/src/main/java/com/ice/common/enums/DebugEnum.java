package com.ice.common.enums;

/**
 * handler的debug枚举
 * 控制着入参,出参与执行过程的打印
 */
public enum DebugEnum {
  /**
   * 入参PACK 1
   */
  IN_PACK,
  /**
   * 执行过程(和节点debug一并使用) 2
   */
  PROCESS,
  /**
   * 结局ROAM 4
   */
  OUT_ROAM,
  /**
   * 结局PACK 8
   */
  OUT_PACK;

  private final byte mask;

  DebugEnum() {
    this.mask = (byte) (1 << ordinal());
  }

  public static boolean filter(DebugEnum debugEnum, byte debug) {
    return (debugEnum.mask & debug) != 0;
  }
}
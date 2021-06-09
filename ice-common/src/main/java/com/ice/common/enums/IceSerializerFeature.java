package com.ice.common.enums;

/**
 * @author zjn
 * 目前SerializerFeature可用的用于位运算的enum只剩两个
 */
public enum IceSerializerFeature {
  /*定制化的数字展示(1<<31)*/
  CUSTOM_NUMBER_SHOW(-2147483648),
  /*1<<30*/
  LAST_REMAIN(1073741824);

  private final int mask;

  IceSerializerFeature(int mask) {
    this.mask = mask;
  }

  public final int getMask() {
    return mask;
  }
}

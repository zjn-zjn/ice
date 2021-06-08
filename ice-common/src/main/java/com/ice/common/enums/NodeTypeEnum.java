package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * 过滤节点类型
 */
public enum NodeTypeEnum {
  /**
   * 关系节点-none
   */
  NONE((byte) 0),
  /**
   * 关系节点-and
   */
  AND((byte) 1),
  /**
   * 关系节点-true
   */
  TRUE((byte) 2),
  /**
   * 关系节点-all
   */
  ALL((byte) 3),
  /**
   * 关系节点-any
   */
  ANY((byte) 4),
  /**
   * 叶子节点-flow
   */
  LEAF_FLOW((byte) 5),
  /**
   * 叶子节点-result
   */
  LEAF_RESULT((byte) 6),
  /**
   * 叶子节点-none
   */
  LEAF_NONE((byte) 7);

  private static final Map<Byte, NodeTypeEnum> MAP = new HashMap<>();

  static {
    for (NodeTypeEnum enums : NodeTypeEnum.values()) {
      MAP.put(enums.getType(), enums);
    }
  }

  private final byte type;

  NodeTypeEnum(byte type) {
    this.type = type;
  }

  public static NodeTypeEnum getEnum(byte type) {
    return MAP.get(type);
  }

  public byte getType() {
    return type;
  }
}

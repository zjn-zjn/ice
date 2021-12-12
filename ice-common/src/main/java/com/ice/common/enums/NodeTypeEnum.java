package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * node type
 */
public enum NodeTypeEnum {
    /*
     * relation node-none
     */
    NONE((byte) 0),
    /*
     * relation node-and
     */
    AND((byte) 1),
    /*
     * relation node-true
     */
    TRUE((byte) 2),
    /*
     * relation node-all
     */
    ALL((byte) 3),
    /*
     * relation node-any
     */
    ANY((byte) 4),
    /*
     * leaf node-flow
     */
    LEAF_FLOW((byte) 5),
    /*
     * leaf node-result
     */
    LEAF_RESULT((byte) 6),
    /*
     * leaf node-none
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

    public static NodeTypeEnum getEnum(Byte type) {
        if (type == null) {
            return null;
        }
        return MAP.get(type);
    }

    public byte getType() {
        return type;
    }
}

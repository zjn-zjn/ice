package com.ice.common.enums;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author waitmoon
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
    LEAF_NONE((byte) 7),
    /*
     * relation node parallel-none
     */
    P_NONE((byte) 8),
    /*
     * relation node parallel-and
     */
    P_AND((byte) 9),
    /*
     * relation node parallel-true
     */
    P_TRUE((byte) 10),
    /*
     * relation node parallel-all
     */
    P_ALL((byte) 11),
    /*
     * relation node parallel-any
     */
    P_ANY((byte) 12);

    private static final Map<Byte, NodeTypeEnum> MAP = new HashMap<>(NodeTypeEnum.values().length);

    private static final Set<Byte> RELATION_SET = new HashSet<>(10);

    static {
        for (NodeTypeEnum enums : NodeTypeEnum.values()) {
            MAP.put(enums.getType(), enums);
        }
        RELATION_SET.add(NONE.type);
        RELATION_SET.add(AND.type);
        RELATION_SET.add(TRUE.type);
        RELATION_SET.add(ALL.type);
        RELATION_SET.add(ANY.type);
        RELATION_SET.add(P_NONE.type);
        RELATION_SET.add(P_AND.type);
        RELATION_SET.add(P_TRUE.type);
        RELATION_SET.add(P_ALL.type);
        RELATION_SET.add(P_ANY.type);
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

    public static boolean isRelation(byte type) {
        return RELATION_SET.contains(type);
    }

    public static boolean isLeaf(byte type) {
        return type == LEAF_FLOW.type || type == LEAF_NONE.type || type == LEAF_RESULT.type;
    }
}

package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author waitmoon
 * node run return
 * REJECT OUTTIME NONE not control process
 */
public enum NodeRunStateEnum {

    /*
     * false
     */
    FALSE((byte) 0),
    /*
     * true
     */
    TRUE((byte) 1),
    /*
     * none
     */
    NONE((byte) 2),
    /*
     * shutdown
     */
    SHUT_DOWN((byte) 3);

    private static final Map<Byte, NodeRunStateEnum> MAP = new HashMap<>();

    static {
        for (NodeRunStateEnum enums : NodeRunStateEnum.values()) {
            MAP.put(enums.getState(), enums);
        }
    }

    private final byte state;

    NodeRunStateEnum(byte state) {
        this.state = state;
    }

    public static NodeRunStateEnum getEnumDefaultShutdown(Byte state) {
        if (state == null) {
            //default shutdown
            return SHUT_DOWN;
        }
        return MAP.get(state);
    }

    public byte getState() {
        return state;
    }
}

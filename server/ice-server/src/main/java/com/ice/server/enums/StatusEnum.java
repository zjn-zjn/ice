package com.ice.server.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author waitmoon
 */
public enum StatusEnum {
    OFFLINE((byte) 0),

    ONLINE((byte) 1),

    DELETED((byte) 2);

    private static final Map<Byte, StatusEnum> MAP = new HashMap<>();

    static {
        for (StatusEnum enums : StatusEnum.values()) {
            MAP.put(enums.getStatus(), enums);
        }
    }

    private final byte status;

    StatusEnum(byte status) {
        this.status = status;
    }

    public static StatusEnum getEnum(byte status) {
        return MAP.get(status);
    }

    public byte getStatus() {
        return status;
    }
}

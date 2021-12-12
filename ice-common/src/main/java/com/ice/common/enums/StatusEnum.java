package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 */
public enum StatusEnum {

    ONLINE((byte) 1),

    OFFLINE((byte) 0);

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

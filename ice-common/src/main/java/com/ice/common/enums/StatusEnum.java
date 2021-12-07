package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * 上下架类型
 */
public enum StatusEnum {
    /*
     * 上架
     */
    ONLINE((byte) 1),
    /*
     * 下架
     */
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

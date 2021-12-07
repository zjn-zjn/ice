package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * 请求类型
 */
public enum RequestTypeEnum {
    /*
     * 正式请求 默认
     */
    FORMAL((byte) 1),
    /*
     * 预演/模拟/测试
     */
    PREVIEW((byte) 2);

    private static final Map<Byte, RequestTypeEnum> MAP = new HashMap<>();

    static {
        for (RequestTypeEnum enums : RequestTypeEnum.values()) {
            MAP.put(enums.getType(), enums);
        }
    }

    private final byte type;

    RequestTypeEnum(byte type) {
        this.type = type;
    }

    public static RequestTypeEnum getEnum(byte type) {
        return MAP.get(type);
    }

    public byte getType() {
        return type;
    }
}

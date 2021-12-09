package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * timetype describe
 */
public enum TimeTypeEnum {
    /*
     * 无时间限制
     */
    NONE((byte) 1),
    /*
     * 大于开始时间
     */
    AFTER_START((byte) 5),
    /*
     * 小于结束时间
     */
    BEFORE_END((byte) 6),
    /*
     * 在开始时间与结束时间之内
     */
    BETWEEN((byte) 7);

    private static final Map<Byte, TimeTypeEnum> MAP = new HashMap<>();

    static {
        for (TimeTypeEnum enums : TimeTypeEnum.values()) {
            MAP.put(enums.getType(), enums);
        }
    }

    private final byte type;

    TimeTypeEnum(byte type) {
        this.type = type;
    }

    public static TimeTypeEnum getEnum(byte type) {
        return MAP.get(type);
    }

    public static TimeTypeEnum getEnumDefaultNone(Byte type) {
        TimeTypeEnum typeEnum = MAP.get(type);
        if (typeEnum == null) {
            return TimeTypeEnum.NONE;
        }
        return typeEnum;
    }

    public byte getType() {
        return type;
    }
}

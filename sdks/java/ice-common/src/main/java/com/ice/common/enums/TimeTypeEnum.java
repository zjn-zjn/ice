package com.ice.common.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author waitmoon
 * timetype describe
 */
public enum TimeTypeEnum {
    /*
     * no limit
     */
    NONE((byte) 1),
    /*
     * after start time
     */
    AFTER_START((byte) 5),
    /*
     * before end time
     */
    BEFORE_END((byte) 6),
    /*
     * between start&end(both closed interval)
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

    public static TimeTypeEnum getEnum(Byte type) {
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

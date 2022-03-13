package com.ice.server.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 */
public enum EditTypeEnum {

    ADD_SON(1),
    EDIT(2),
    DELETE(3),
    ADD_FORWARD(4),
    EXCHANGE(5),
    MOVE(6);
    private static final Map<Integer, EditTypeEnum> MAP = new HashMap<>();

    static {
        for (EditTypeEnum enums : EditTypeEnum.values()) {
            MAP.put(enums.getType(), enums);
        }
    }

    private final int type;

    EditTypeEnum(int type) {
        this.type = type;
    }

    public static EditTypeEnum getEnum(int type) {
        return MAP.get(type);
    }

    public int getType() {
        return type;
    }
}

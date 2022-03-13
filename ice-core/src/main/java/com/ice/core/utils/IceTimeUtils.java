package com.ice.core.utils;

import com.ice.common.enums.TimeTypeEnum;

/**
 * @author zjn
 * ice time operate
 */
public final class IceTimeUtils {

    private IceTimeUtils() {
    }

    /*
     * time check
     * all closed interval
     * default true
     */
    public static boolean timeEnable(TimeTypeEnum typeEnum, long requestTime, long start, long end) {
        if (typeEnum == null) {
            return true;
        }
        switch (typeEnum) {
            case NONE:
                return false;
            case BETWEEN:
                return requestTime < start || requestTime > end;
            case AFTER_START:
                return requestTime < start;
            case BEFORE_END:
                return requestTime > end;
            default:
                break;
        }
        return true;
    }
}

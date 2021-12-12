package com.ice.common.enums;

/**
 * handler`s debug
 * control pack process print
 */
public enum DebugEnum {
    /*
     * print input pack 1
     */
    IN_PACK,
    /*
     * print process info(combine with node debug) 2
     */
    PROCESS,
    /*
     * print output roam 4
     */
    OUT_ROAM,
    /*
     * print output pack 8
     */
    OUT_PACK;

    private final byte mask;

    DebugEnum() {
        this.mask = (byte) (1 << ordinal());
    }

    public static boolean filter(DebugEnum debugEnum, byte debug) {
        return (debugEnum.mask & debug) != 0;
    }
}
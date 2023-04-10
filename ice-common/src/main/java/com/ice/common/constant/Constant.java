package com.ice.common.constant;

public final class Constant {
    public static final String REGEX_COMMA = ",";

    public static String removeLast(StringBuilder sb) {
        if (sb == null || sb.length() == 0) {
            return "";
        }
        return sb.substring(0, sb.length() - 1);
    }
}

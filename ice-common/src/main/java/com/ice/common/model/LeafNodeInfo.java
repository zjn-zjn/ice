package com.ice.common.model;

import lombok.Data;

import java.util.List;

/**
 * @author waitmoon
 */
@Data
public final class LeafNodeInfo {
    private byte type;
    private String clazz;
    private String name;
    private String desc;
    private List<FieldInfo> fields;
    /**
     * @author waitmoon
     */
    @Data
    public static class FieldInfo {
        private String field;
        private String name;
        private String desc;
    }
}

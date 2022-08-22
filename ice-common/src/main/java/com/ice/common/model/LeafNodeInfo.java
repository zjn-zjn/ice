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
    private List<IceFieldInfo> iceFields;
    private List<HideFieldInfo> hideFields;

    @Data
    public static class IceFieldInfo {
        //field name
        private String field;
        //name
        private String name;
        //describe
        private String desc;
        //client type clazz name, first from config, then declared type
        private String type;
    }

    @Data
    public static class HideFieldInfo {
        //field name
        private String field;
        //client type clazz name, first from config, then declared type
        private String type;
    }
}

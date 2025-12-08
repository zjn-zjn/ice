package com.ice.common.model;

import lombok.Data;

import java.util.List;

/**
 * @author waitmoon
 */
@Data
public final class LeafNodeInfo {
    private Byte type;
    private String clazz;
    private String name;
    private String desc;
    /**
     * Display order for UI sorting, lower value appears first.
     */
    private Integer order;
    private List<IceFieldInfo> iceFields;
    private List<IceFieldInfo> hideFields;

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

        //assemble in server admin node conf
        private Object value; //json value for web
        private Boolean valueNull;
    }
}

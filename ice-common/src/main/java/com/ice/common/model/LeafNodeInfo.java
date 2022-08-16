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
        //field name
        private String field;
        //name
        private String name;
        //describe
        private String desc;
        //client type clazz name, first from config, then declared type
        private String type;
        //value of json while show client conf
        private String value;
    }
}

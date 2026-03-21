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
    private List<RoamKeyMeta> roamKeys;

    @Data
    public static class RoamKeyMeta {
        private String direction;    // "read" | "write" | "read_write"
        private String accessMode;   // "direct" | "union"
        private String accessMethod; // "get" | "getDeep" | "put" | "putDeep"
        private List<KeyPart> keyParts;
    }

    @Data
    public static class KeyPart {
        private String type;         // "literal" | "field" | "roamDerived" | "composite"
        private String value;        // type=literal
        private String ref;          // type=field, class field name
        private String fromKey;      // type=roamDerived, source roam key
        private List<KeyPart> parts; // type=composite
    }

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

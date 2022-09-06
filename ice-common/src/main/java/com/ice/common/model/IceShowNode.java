package com.ice.common.model;

import lombok.Data;

import java.util.List;

/**
 * @author waitmoon
 */
@Data
public class IceShowNode {

    private NodeShowConf showConf;

    private IceShowNode forward;

    private List<IceShowNode> children;

    private Long start;

    private Long end;

    private Long parentId;

    private Long nextId;

    private Integer index;

    private String sonIds;

    private Long forwardId;

    private Byte timeType;

    /**
     * @author waitmoon
     */
    @Data
    public static final class NodeShowConf {
        private Long nodeId;

        private Boolean debug;

        private Byte errorState;

        private Boolean inverse;

        private Byte nodeType;

        private String nodeName;

        private String labelName;

        private String confName;

        private String confField;
        //updating or not
        private Boolean updating;

        private Boolean haveMeta;

        private LeafNodeInfo nodeInfo;
    }
}

package com.ice.common.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class IceShowNode implements Serializable {

    private NodeConf showConf;

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

    @Data
    public static final class NodeConf implements Serializable {
        private Long nodeId;

        private Boolean debug;

        private Boolean inverse;

        private Byte nodeType;

        private String nodeName;

        private String labelName;

        private String confName;

        private String confField;
    }
}

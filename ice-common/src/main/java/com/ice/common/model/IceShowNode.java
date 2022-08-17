package com.ice.common.model;

import com.ice.common.enums.NodeRunStateEnum;
import lombok.Data;

import java.util.List;

/**
 * @author waitmoon
 */
@Data
public class IceShowNode {

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

    private Boolean edit;

    /**
     * @author waitmoon
     */
    @Data
    public static final class NodeConf {
        private Long nodeId;

        private Boolean debug;

        private Boolean inverse;

        private NodeRunStateEnum errorStateEnum;

        private Byte nodeType;

        private String nodeName;

        private String labelName;

        private String confName;

        private String confField;
    }
}

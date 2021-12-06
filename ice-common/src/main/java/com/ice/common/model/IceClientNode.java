package com.ice.common.model;

import com.ice.common.enums.TimeTypeEnum;
import lombok.Data;

import java.util.List;

@Data
public class IceClientNode {

    //info in remote client
    private long iceNodeId;

    private TimeTypeEnum iceTimeTypeEnum;

    private long iceStart;

    private long iceEnd;

    private boolean iceNodeDebug;

    private boolean iceInverse;

    private IceClientNode iceForward;

    private List<IceClientNode> children;

    //info in server

    private Byte nodeType;

    private Long parentId;

    private Long nextId;

    private String nodeName;

    private String confName;

    private String confField;
}

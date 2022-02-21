package com.ice.common.model;

import lombok.Data;

import java.util.List;

@Data
public class IceShowNode {

    //info in remote client
    private Long id;

    private Byte timeType;

    private Long start;

    private Long end;

    private Boolean debug;

    private Boolean inverse;

    private IceShowNode forward;

    private List<IceShowNode> children;

    private Long forwardId;

    private Byte nodeType;

    private Long parentId;

    private Integer index;

    private Long nextId;

    private String sonIds;

    private String name;

    private String labelName;

    private String confName;

    private String confField;
}

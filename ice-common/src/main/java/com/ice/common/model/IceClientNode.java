package com.ice.common.model;

import lombok.Data;

import java.util.List;

@Data
public class IceClientNode {

    //info in remote client
    private String id;

    private Byte timeType;

    private Long start;

    private Long end;

    private Boolean debug;

    private Boolean inverse;

    private IceClientNode forward;

    private List<IceClientNode> children;

    //info in server
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

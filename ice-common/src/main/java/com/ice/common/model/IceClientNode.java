package com.ice.common.model;

import lombok.Data;

import java.util.List;

@Data
public class IceClientNode {

    //info in remote client
    private long id;

    private Byte timeType;

    private Long start;

    private Long end;

    private Boolean debug;

    private Boolean inverse;

    private IceClientNode forward;

    private List<IceClientNode> children;

    //info in server
    private Byte type;

    private Long parentId;

    private Long nextId;

    private String sonIds;

    private String name;

    private String confName;

    private String confField;
}

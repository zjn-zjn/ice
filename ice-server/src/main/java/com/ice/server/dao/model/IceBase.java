package com.ice.server.dao.model;

import java.util.Date;

import lombok.Data;

@Data
public class IceBase {
    private Long id;

    private String name;

    private Integer app;

    private String scenes;

    private Byte status;

    private Long confId;

    private Byte timeType;

    private Date start;

    private Date end;

    private Byte debug;

    private Long priority;

    private Date createAt;

    private Date updateAt;
}
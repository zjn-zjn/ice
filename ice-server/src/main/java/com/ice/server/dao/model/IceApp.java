package com.ice.server.dao.model;

import lombok.Data;

import java.util.Date;

@Data
public class IceApp {
    private Long id;

    private String name;

    private String info;

    private Boolean status;

    private Date createAt;

    private Date updateAt;
}
package com.ice.server.dao.model;

import lombok.Data;

import java.util.Date;

@Data
public class IceConf {
    private Long id;

    private Integer app;

    private String name;

    private String sonIds;

    private String linkIds;

    private String unlinkIds;

    private Byte type;

    private Byte status;

    private Byte inverse;

    private String confName;

    private String confField;

    private Long forwardId;

    private Byte timeType;

    private Date start;

    private Date end;

    private Byte debug;

    private Date createAt;

    private Date updateAt;

    //mix from update/conf
    public Long getMixId() {
        if (confId != null) {
            return confId;
        }
        return id;
    }

    public boolean isUpdatingConf() {
        return confId != null;
    }

    //only in ice_conf_update
    private Long iceId;
    private Long confId;
}
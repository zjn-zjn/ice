package com.ice.common.dto;

import lombok.Data;

/**
 * @author waitmoon
 */
@Data
public final class IceConfDto {

    private Long id;

    private String sonIds;

    private Byte type;

    private String confName;

    private String confField;

    private Byte timeType;

    private Long start;

    private Long end;

    private Long forwardId;

    private Byte debug;

    private Byte errorState;

    private Boolean inverse;

    private String name;

    private Integer app;

    /**
     * 状态: 1-online, 0-offline, -1-deleted
     */
    private Byte status;

    /**
     * 创建时间戳(毫秒)
     */
    private Long createAt;

    /**
     * 更新时间戳(毫秒)
     */
    private Long updateAt;

    //only in ice_conf_update
    private Long iceId;
    private Long confId;
}

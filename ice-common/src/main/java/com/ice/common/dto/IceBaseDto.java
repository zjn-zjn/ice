package com.ice.common.dto;

import lombok.Data;

/**
 * @author waitmoon
 */
@Data
public final class IceBaseDto {

    private Long id;

    private String scenes;

    private Long confId;

    private Byte timeType;

    private Long start;

    private Long end;

    private Byte debug;

    private Long priority;

    private Integer app;

    private String name;

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
}

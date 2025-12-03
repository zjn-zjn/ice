package com.ice.common.dto;

import lombok.Data;

/**
 * 应用信息DTO
 *
 * @author waitmoon
 */
@Data
public final class IceAppDto {

    /**
     * 应用ID
     */
    private Integer id;

    /**
     * 应用名称
     */
    private String name;

    /**
     * 应用描述
     */
    private String info;

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


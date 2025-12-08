package com.ice.common.dto;

import lombok.Data;

/**
 * 发布历史DTO
 *
 * @author waitmoon
 */
@Data
public final class IcePushHistoryDto {

    /**
     * 发布记录ID
     */
    private Long id;

    /**
     * 应用ID
     */
    private Integer app;

    /**
     * Ice ID
     */
    private Long iceId;

    /**
     * 发布原因
     */
    private String reason;

    /**
     * 发布数据JSON
     */
    private String pushData;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 创建时间戳(毫秒)
     */
    private Long createAt;
}


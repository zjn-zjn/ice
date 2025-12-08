package com.ice.common.dto;

import lombok.Data;

/**
 * Push history DTO.
 *
 * @author waitmoon
 */
@Data
public final class IcePushHistoryDto {

    /**
     * Push record ID.
     */
    private Long id;

    /**
     * Application ID.
     */
    private Integer app;

    /**
     * Ice ID.
     */
    private Long iceId;

    /**
     * Push reason.
     */
    private String reason;

    /**
     * Push data in JSON format.
     */
    private String pushData;

    /**
     * Operator.
     */
    private String operator;

    /**
     * Creation timestamp in milliseconds.
     */
    private Long createAt;
}


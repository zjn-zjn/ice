package com.ice.common.dto;

import lombok.Data;

/**
 * Application info DTO.
 *
 * @author waitmoon
 */
@Data
public final class IceAppDto {

    /**
     * Application ID.
     */
    private Integer id;

    /**
     * Application name.
     */
    private String name;

    /**
     * Application description.
     */
    private String info;

    /**
     * Status: 1-online, 0-offline, -1-deleted
     */
    private Byte status;

    /**
     * Creation timestamp in milliseconds.
     */
    private Long createAt;

    /**
     * Update timestamp in milliseconds.
     */
    private Long updateAt;
}


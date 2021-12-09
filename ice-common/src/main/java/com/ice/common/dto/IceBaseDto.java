package com.ice.common.dto;

import com.ice.common.enums.TimeTypeEnum;
import lombok.Data;

/**
 * @author zjn
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
}

package com.ice.common.model;

import com.ice.common.enums.TimeTypeEnum;
import lombok.Data;

import java.util.Set;

@Data
public class IceClientHandler {
    private Long iceId;
    private Set<String> scenes;
    private byte debug;
    private Long start;
    private Long end;
    private TimeTypeEnum iceTimeTypeEnum;
    private IceClientNode root;
}

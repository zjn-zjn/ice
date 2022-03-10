package com.ice.rmi.common.model;

import com.ice.common.dto.IceTransferDto;
import com.ice.core.context.IcePack;
import lombok.Data;

import java.io.Serializable;

@Data
public class ClientOneWayRequest implements Serializable {

    private String name;
    private int app;
    private IceTransferDto dto;
    private String clazz;
    private byte type;
    private Long confId;
    private IcePack pack;
    private String lock;
}

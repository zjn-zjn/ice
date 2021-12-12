package com.ice.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author zjn
 */
@Data
@NoArgsConstructor
@Deprecated
public class IceBaseVo {

    private Integer app;
    private Long id;
    private String name;
    private String scenes;
    private Byte timeType = 1;
    private Date start;
    private Date end;
    private Byte status = 1;
    private Byte debug;
    private Long confId;
}

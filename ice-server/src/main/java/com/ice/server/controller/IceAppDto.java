package com.ice.server.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zjn
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Deprecated
public class IceAppDto {
    private long app;
    private String appName;
    private String info;
}

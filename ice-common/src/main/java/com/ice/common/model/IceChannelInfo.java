package com.ice.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public final class IceChannelInfo {
    private int app;
    private String address;
    private long lastUpdateTime;
}

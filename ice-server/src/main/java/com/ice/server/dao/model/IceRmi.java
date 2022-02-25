package com.ice.server.dao.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IceRmi {
    private Long id;

    private Integer app;

    private String host;

    private Integer port;

    public IceRmi(Integer app, String host, Integer port) {
        this.app = app;
        this.host = host;
        this.port = port;
    }
}
package com.ice.server.dao.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

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
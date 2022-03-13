package com.ice.rmi.common.model;

import com.ice.rmi.common.enums.RmiNetModeEnum;
import com.ice.rmi.common.client.IceRmiClientService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ClientInfo implements Serializable {

    public ClientInfo(int app, String address, RmiNetModeEnum mode) {
        this.app = app;
        this.address = address;
        this.mode = mode;
    }
    private int app;
    private RmiNetModeEnum mode;
    private String address;
    private IceRmiClientService clientService;
}

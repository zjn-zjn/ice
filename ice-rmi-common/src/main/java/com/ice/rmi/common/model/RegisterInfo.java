package com.ice.rmi.common.model;

import com.ice.rmi.common.client.IceRmiClientService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
//@AllArgsConstructor
@NoArgsConstructor
public class RegisterInfo implements Serializable {

    public RegisterInfo(int app, String address) {
        this.app = app;
        this.address = address;
    }

    private int app;
    private String address;
//    private IceRmiClientService clientService;
}

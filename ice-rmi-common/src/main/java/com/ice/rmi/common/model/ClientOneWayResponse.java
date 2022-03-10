package com.ice.rmi.common.model;

import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ClientOneWayResponse implements Serializable {

    private String address;

    private Pair<Integer, String> clazzCheck;

    private List<String> updateResults;

    private IceShowConf showConf;

    private List<IceContext> mockResults;
}

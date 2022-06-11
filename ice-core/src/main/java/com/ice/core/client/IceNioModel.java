package com.ice.core.client;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import lombok.Data;

import java.util.List;

/**
 * @author zjn
 * client and server transport model
 */
@Data
public class IceNioModel {

    private String id;

    private NioType type;

    private NioOps ops;

    private int app;

    private String address;

    private String uniqueAddress;

    private IceTransferDto updateDto;

    private String clazz;

    private Byte nodeType;

    private Long confId;

    private IcePack pack;

    private IceTransferDto initDto;

    private Pair<Integer, String> clazzCheck;

    private List<String> updateErrors;

    private IceShowConf showConf;

    private List<IceContext> mockResults;
}

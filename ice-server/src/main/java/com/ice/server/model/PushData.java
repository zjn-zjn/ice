package com.ice.server.model;

import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;
import lombok.Data;

import java.util.List;

/**
 * @author zjn
 */
@Data
public class PushData {

    private IceBase base;

    private List<IceConf> confs;
}

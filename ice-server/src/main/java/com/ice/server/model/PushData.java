package com.ice.server.model;

import com.ice.common.dto.IceBaseDto;
import com.ice.common.dto.IceConfDto;
import lombok.Data;

import java.util.Collection;

/**
 * @author zjn
 */
@Data
public class PushData {

    private Integer app;

    private IceBaseDto base;

    private Collection<IceConfDto> confs;

    private Collection<IceConfDto> confUpdates;
}

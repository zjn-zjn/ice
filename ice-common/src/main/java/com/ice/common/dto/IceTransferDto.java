package com.ice.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collection;

/**
 * @author zjn
 */
@Data
public final class IceTransferDto implements Serializable {

    private long version;

    private Collection<IceConfDto> insertOrUpdateConfs;

    private Collection<Long> deleteConfIds;

    private Collection<IceBaseDto> insertOrUpdateBases;

    private Collection<Long> deleteBaseIds;
}

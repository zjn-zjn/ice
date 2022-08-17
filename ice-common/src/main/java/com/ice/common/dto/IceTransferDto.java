package com.ice.common.dto;

import lombok.Data;

import java.util.Collection;

/**
 * @author waitmoon
 */
@Data
public final class IceTransferDto {

    private long version;

    private Collection<IceConfDto> insertOrUpdateConfs;

    private Collection<Long> deleteConfIds;

    private Collection<IceBaseDto> insertOrUpdateBases;

    private Collection<Long> deleteBaseIds;
}

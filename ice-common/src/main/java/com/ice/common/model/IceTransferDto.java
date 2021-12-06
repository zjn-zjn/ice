package com.ice.common.model;

import lombok.Data;

import java.util.Collection;
import java.util.List;

/**
 * @author zjn
 */
@Data
public final class IceTransferDto {

  private long version;

  private Collection<IceConfDto> insertOrUpdateConfs;

  private List<Long> deleteConfIds;

  private Collection<IceBaseDto> insertOrUpdateBases;

  private List<Long> deleteBaseIds;
}

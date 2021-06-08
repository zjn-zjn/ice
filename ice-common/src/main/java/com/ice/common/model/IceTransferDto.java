package com.ice.common.model;

import lombok.Data;

import java.util.List;

/**
 * @author zjn
 */
@Data
public final class IceTransferDto {

  private long version;

  private List<IceConfDto> insertOrUpdateConfs;

  private List<Long> deleteConfIds;

  private List<IceBaseDto> insertOrUpdateBases;

  private List<Long> deleteBaseIds;
}

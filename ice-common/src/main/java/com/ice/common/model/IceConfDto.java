package com.ice.common.model;

import lombok.Data;

/**
 * @author zjn
 */
@Data
public final class IceConfDto {

  private Long id;

  private String sonIds;

  private Byte type;

  private Byte status;

  private String confName;

  private String confField;

  private Byte timeType;

  private Long start;

  private Long end;

  private Long forwardId;

  private Integer complex;

  private Byte debug;

  private Byte inverse;
}

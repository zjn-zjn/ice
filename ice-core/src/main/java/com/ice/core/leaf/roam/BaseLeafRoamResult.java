package com.ice.core.leaf.roam;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafRoamResult extends BaseLeafPackResult {

  @Override
  protected boolean doPackResult(IcePack pack) throws InvocationTargetException, IllegalAccessException {
    return doRoamResult(pack.getRoam());
  }

/*
   * process leaf result with roam
   *
   * @param roam 传递roam
   */
  protected abstract boolean doRoamResult(IceRoam roam) throws InvocationTargetException, IllegalAccessException;
}

package com.ice.core.leaf.roam;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafRoamFlow extends BaseLeafPackFlow {

  @Override
  protected boolean doPackFlow(IcePack pack) throws InvocationTargetException, IllegalAccessException {
    return doRoamFlow(pack.getRoam());
  }

/*
   * process leaf flow with roam
   *
   * @param roam 传递roam
   */
  protected abstract boolean doRoamFlow(IceRoam roam) throws InvocationTargetException, IllegalAccessException;
}

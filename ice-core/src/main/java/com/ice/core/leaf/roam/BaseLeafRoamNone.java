package com.ice.core.leaf.roam;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackNone;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafRoamNone extends BaseLeafPackNone {

  @Override
  protected void doPackNone(IcePack pack) throws InvocationTargetException, IllegalAccessException {
    doRoamNone(pack.getRoam());
  }

/*
   * process leaf none with roam
   *
   * @param roam 传递roam
   */
  protected abstract void doRoamNone(IceRoam roam) throws InvocationTargetException, IllegalAccessException;
}

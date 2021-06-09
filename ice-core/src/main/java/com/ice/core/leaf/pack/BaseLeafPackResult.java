package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackResult extends BaseLeafResult {

  @Override
  protected boolean doResult(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    return doPackResult(cxt.getPack());
  }

/*
   * process leaf result with pack
   *
   * @param pack 包裹
   */
  protected abstract boolean doPackResult(IcePack pack) throws InvocationTargetException, IllegalAccessException;
}

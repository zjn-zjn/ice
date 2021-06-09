package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackFlow extends BaseLeafFlow {

  @Override
  protected boolean doFlow(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    return doPackFlow(cxt.getPack());
  }

/*
   * process leaf flow with pack
   *
   * @param pack 包裹
   * @return
   */
  protected abstract boolean doPackFlow(IcePack pack) throws InvocationTargetException, IllegalAccessException;
}

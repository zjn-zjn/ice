package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafNone;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackNone extends BaseLeafNone {

  @Override
  protected void doNone(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    doPackNone(cxt.getPack());
  }

  /**
   * process leaf none with pack
   *
   * @param pack
   * @return
   */
  protected abstract void doPackNone(IcePack pack) throws InvocationTargetException, IllegalAccessException;
}

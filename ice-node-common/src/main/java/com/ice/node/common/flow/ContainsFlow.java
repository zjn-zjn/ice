package com.ice.node.common.flow;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collection;

/**
 * @author zjn
 * 判断key对应的值是否在set中
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContainsFlow extends BaseLeafRoamFlow {

  private Object key;
/**
   * Collection
   * eg:Set[1,2]
   */
  private Object collection;

/*
   * 叶子节点流程处理
   *
   * @param roam 传递roam
   */
  @Override
  protected boolean doRoamFlow(IceRoam roam) {
    Collection<Object> sets = roam.getUnion(collection);
    if (sets == null || sets.isEmpty()) {
      return false;
    }
    return sets.contains(roam.getUnion(key));
  }
}

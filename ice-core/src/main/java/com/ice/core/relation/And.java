package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 * 流程->AND关系
 * 有一个子节点返回FALSE将中断执行
 * 有FALSE->FALSE
 * 无FALSE有TRUE->TRUE
 * 无子节点->NONE
 * 全NONE->NONE
 */
public final class And extends BaseRelation {

  /**
   * process relation and
   *
   * @param cxt
   */
  @Override
  protected NodeRunStateEnum processNode(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    IceLinkedList<BaseNode> children = this.getChildren();
    if (children == null || children.isEmpty()) {
      return NodeRunStateEnum.NONE;
    }
    boolean hasTrue = false;
    int loop = this.getLoop();
    if (loop == 0) {
      for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
        BaseNode node = listNode.item;
        if (node != null) {
          NodeRunStateEnum stateEnum = node.process(cxt);
          if (stateEnum == NodeRunStateEnum.FALSE) {
            return NodeRunStateEnum.FALSE;
          }
          if (!hasTrue) {
            hasTrue = stateEnum == NodeRunStateEnum.TRUE;
          }
        }
      }
    } else if (loop < 0) {
      loop = 0;
      while (true) {
        loop++;
        cxt.setCurrentLoop(loop);
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
             listNode != null; listNode = listNode.next) {
          BaseNode node = listNode.item;
          if (node != null) {
            NodeRunStateEnum stateEnum = node.process(cxt);
            if (stateEnum == NodeRunStateEnum.FALSE) {
              return NodeRunStateEnum.FALSE;
            }
            if (!hasTrue) {
              hasTrue = stateEnum == NodeRunStateEnum.TRUE;
            }
          }
        }
      }
    } else {
      for (; loop > 0; loop--) {
        cxt.setCurrentLoop(loop);
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
             listNode != null; listNode = listNode.next) {
          BaseNode node = listNode.item;
          if (node != null) {
            NodeRunStateEnum stateEnum = node.process(cxt);
            if (stateEnum == NodeRunStateEnum.FALSE) {
              return NodeRunStateEnum.FALSE;
            }
            if (!hasTrue) {
              hasTrue = stateEnum == NodeRunStateEnum.TRUE;
            }
          }
        }
      }
    }

    if (hasTrue) {
      return NodeRunStateEnum.TRUE;
    }
    return NodeRunStateEnum.NONE;
  }
}

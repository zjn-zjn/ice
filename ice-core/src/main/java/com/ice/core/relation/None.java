package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 * None关系
 * 子节点全部执行
 * 返回NONE
 */
public final class None extends BaseRelation {
/*
   * process relation none
   *
   *
   */
  @Override
  protected NodeRunStateEnum processNode(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    IceLinkedList<BaseNode> children = this.getChildren();
    if (children == null || children.isEmpty()) {
      return NodeRunStateEnum.NONE;
    }
    int loop = this.getLoop();
    if (loop == 0) {
      for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
        BaseNode node = listNode.item;
        if (node != null) {
          node.process(cxt);
        }
      }
    } else {
      for (int i = 0; i < loop; i++) {
        cxt.setCurrentLoop(i);
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
             listNode != null; listNode = listNode.next) {
          BaseNode node = listNode.item;
          if (node != null) {
            node.process(cxt);
          }
        }
      }
    }

    return NodeRunStateEnum.NONE;
  }
}

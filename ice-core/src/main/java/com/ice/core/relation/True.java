package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 * 结果--TRUE关系
 * 子节点全部执行
 * 无子节点--TRUE
 * 有子节点--TRUE
 */
public final class True extends BaseRelation {
/*
   * process relation true
   *
   *
   */
  @Override
  protected NodeRunStateEnum processNode(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    IceLinkedList<BaseNode> children = this.getChildren();
    if (children == null || children.isEmpty()) {
      return NodeRunStateEnum.TRUE;
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
    return NodeRunStateEnum.TRUE;
  }
}

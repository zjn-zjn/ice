package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

/**
 * @author zjn
 * relation AND
 * return false on first false
 * have FALSE--FALSE
 * without FALSE have TRUE--TRUE
 * without children--NONE
 * all NONE--NONE
 */
public final class And extends BaseRelation {

    /*
     * process relation and
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext cxt) {
        IceLinkedList<BaseNode> children = this.getChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.NONE;
        }
        if (children.getSize() == 1) {
            BaseNode node = children.get(0);
            if (node == null) {
                return NodeRunStateEnum.NONE;
            }
            return node.process(cxt);
        }
        boolean hasTrue = false;
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

        if (hasTrue) {
            return NodeRunStateEnum.TRUE;
        }
        return NodeRunStateEnum.NONE;
    }
}

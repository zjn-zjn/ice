package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

/**
 * @author zjn
 * relation ANY
 * return true on first true
 * have TRUE--TRUE
 * without TRUE have FALSE--FALSE
 * without children--NONE
 * all NONE--NONE
 */
public final class Any extends BaseRelation {
    /*
     * process relation any
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext cxt) {
        IceLinkedList<BaseNode> children = this.getChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.NONE;
        }
        boolean hasFalse = false;
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                NodeRunStateEnum stateEnum = node.process(cxt);
                if (stateEnum == NodeRunStateEnum.TRUE) {
                    return NodeRunStateEnum.TRUE;
                }
                if (!hasFalse) {
                    hasFalse = stateEnum == NodeRunStateEnum.FALSE;
                }
            }
        }

        if (hasFalse) {
            return NodeRunStateEnum.FALSE;
        }
        return NodeRunStateEnum.NONE;
    }
}

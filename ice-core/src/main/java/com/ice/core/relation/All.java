package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

/**
 * @author waitmoon
 * relation ALL
 * all child will execute
 * have TRUE--TRUE
 * without TRUE have FALSE--FALSE
 * without chilren--NONE
 * all NONE--NONE
 */
public final class All extends BaseRelation {
    /*
     * process relation all
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext ctx) {
        IceLinkedList<BaseNode> children = this.getIceChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.NONE;
        }
        boolean hasTrue = false;
        boolean hasFalse = false;
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                NodeRunStateEnum stateEnum = node.process(ctx);
                if (!hasTrue) {
                    hasTrue = stateEnum == NodeRunStateEnum.TRUE;
                }
                if (!hasFalse) {
                    hasFalse = stateEnum == NodeRunStateEnum.FALSE;
                }
            }
        }
        if (hasTrue) {
            return NodeRunStateEnum.TRUE;
        }
        if (hasFalse) {
            return NodeRunStateEnum.FALSE;
        }
        return NodeRunStateEnum.NONE;
    }
}

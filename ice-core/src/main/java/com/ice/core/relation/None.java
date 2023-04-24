package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

/**
 * @author waitmoon
 * relation NONE
 * all child will execute
 * return NONE
 */
public final class None extends BaseRelation {
    /*
     * process relation none
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext ctx) {
        IceLinkedList<BaseNode> children = this.getIceChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.NONE;
        }
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                node.process(ctx);
            }
        }

        return NodeRunStateEnum.NONE;
    }
}

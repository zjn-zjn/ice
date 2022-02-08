package com.ice.core.relation;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceLinkedList;

/**
 * @author zjn
 * relation TRUE
 * all child will execute
 * return TRUE
 */
public final class True extends BaseRelation {
    /*
     * process relation true
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext cxt) {
        IceLinkedList<BaseNode> children = this.getChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.TRUE;
        }
        if (children.getSize() == 1) {
            children.get(0).process(cxt);
            return NodeRunStateEnum.TRUE;
        }
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                node.process(cxt);
            }
        }

        return NodeRunStateEnum.TRUE;
    }
}

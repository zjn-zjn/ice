package com.ice.core.relation.parallel;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.common.model.Pair;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.IceLinkedList;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author waitmoon
 * relation P_TRUE(parallel execute)
 * all child will execute
 * return TRUE
 */
public final class ParallelTrue extends BaseRelation {
    /*
     * process relation true
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext ctx) {
        IceLinkedList<BaseNode> children = this.getChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.TRUE;
        }
        if (children.getSize() == 1) {
            BaseNode node = children.get(0);
            if (node == null) {
                return NodeRunStateEnum.TRUE;
            }
            return node.process(ctx);
        }
        List<Pair<Long, Future<?>>> futurePairs = new LinkedList<>();
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                futurePairs.add(new Pair<>(node.findIceNodeId(), IceExecutor.submitNodeRunnable(node, ctx)));
            }
        }
        long nodeId = 0;
        try {
            for (Pair<Long, Future<?>> pair : futurePairs) {
                nodeId = pair.getKey();
                pair.getValue().get();
            }
        } catch (Exception e) {
            throw new NodeException(nodeId, e);
        }

        return NodeRunStateEnum.TRUE;
    }
}

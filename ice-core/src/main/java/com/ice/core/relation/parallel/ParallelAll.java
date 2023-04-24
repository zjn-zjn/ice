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
 * relation P_ALL(parallel execute)
 * all child will execute
 * have TRUE--TRUE
 * without TRUE have FALSE--FALSE
 * without chilren--NONE
 * all NONE--NONE
 */
public final class ParallelAll extends BaseRelation {
    /*
     * process relation all
     */
    @Override
    protected NodeRunStateEnum processNode(IceContext ctx) {
        IceLinkedList<BaseNode> children = this.getIceChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.NONE;
        }
        if (children.getSize() == 1) {
            BaseNode node = children.get(0);
            if (node == null) {
                return NodeRunStateEnum.NONE;
            }
            return node.process(ctx);
        }
        List<Pair<Long, Future<NodeRunStateEnum>>> pairList = new LinkedList<>();
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                pairList.add(new Pair<>(node.findIceNodeId(), IceExecutor.submitNodeCallable(node, ctx)));
            }
        }
        boolean hasTrue = false;
        boolean hasFalse = false;
        long nodeId = 0;
        try {
            for (Pair<Long, Future<NodeRunStateEnum>> pair : pairList) {
                nodeId = pair.getKey();
                NodeRunStateEnum stateEnum;
                stateEnum = pair.getValue().get();
                if (!hasTrue) {
                    hasTrue = stateEnum == NodeRunStateEnum.TRUE;
                }
                if (!hasFalse) {
                    hasFalse = stateEnum == NodeRunStateEnum.FALSE;
                }
            }
        } catch (Exception e) {
            throw new NodeException(nodeId, e);
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

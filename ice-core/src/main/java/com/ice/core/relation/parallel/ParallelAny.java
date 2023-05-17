package com.ice.core.relation.parallel;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.common.model.Pair;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.IceLinkedList;

import java.util.*;
import java.util.concurrent.Future;

/**
 * @author waitmoon
 * relation P_ANY(parallel execute)
 * return true on first true
 * have TRUE--TRUE
 * without TRUE have FALSE--FALSE
 * without children--NONE
 * all NONE--NONE
 */
public final class ParallelAny extends BaseRelation {
    /*
     * process relation any
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
        LinkedList<Pair<Long, Future<NodeRunStateEnum>>> futurePairs = new LinkedList<>();
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                futurePairs.add(new Pair<>(node.findIceNodeId(), IceExecutor.submitNodeCallable(node, ctx)));
            }
        }
        boolean hasFalse = false;
        int size = futurePairs.size();
        long nodeId = 0;
        try {
            while (size > 0) {
                Set<Pair<Long, Future<NodeRunStateEnum>>> doneFuturePairs = new HashSet<>();
                for (Pair<Long, Future<NodeRunStateEnum>> pair : futurePairs) {
                    nodeId = pair.getKey();
                    Future<NodeRunStateEnum> future = pair.getValue();
                    if (future.isDone()) {
                        NodeRunStateEnum stateEnum;
                        stateEnum = future.get();
                        if (stateEnum == NodeRunStateEnum.TRUE) {
                            return NodeRunStateEnum.TRUE;
                        }
                        if (!hasFalse) {
                            hasFalse = stateEnum == NodeRunStateEnum.FALSE;
                        }
                        doneFuturePairs.add(pair);
                        size--;
                    }
                }
                if (size > 0) {
                    for (Pair<Long, Future<NodeRunStateEnum>> pair : doneFuturePairs) {
                        futurePairs.remove(pair);
                    }
                    Thread.yield();
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new NodeException(nodeId, e);
        }

        if (hasFalse) {
            return NodeRunStateEnum.FALSE;
        }
        return NodeRunStateEnum.NONE;
    }
}

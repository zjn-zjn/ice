package com.ice.core.relation.parallel;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.IceLinkedList;
import com.ice.common.model.Pair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author zjn
 * relation P_AND(parallel execute)
 * return false on first false
 * have FALSE--FALSE
 * without FALSE have TRUE--TRUE
 * without children--NONE
 * all NONE--NONE
 */
public final class ParallelAnd extends BaseRelation {
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
        LinkedList<Pair<Long, Future<NodeRunStateEnum>>> futurePairs = new LinkedList<>();
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                futurePairs.add(new Pair<>(node.findIceNodeId(), IceExecutor.submitNodeCallable(node, cxt)));
            }
        }
        boolean hasTrue = false;
        int size = futurePairs.size();
        long nodeId = 0;
        try {
            while (size > 0) {
                List<Pair<Long, Future<NodeRunStateEnum>>> removeFuturePairs = new ArrayList<>(size);
                for (Pair<Long, Future<NodeRunStateEnum>> pair : futurePairs) {
                    nodeId = pair.getKey();
                    Future<NodeRunStateEnum> future = pair.getValue();
                    if (future.isDone()) {
                        NodeRunStateEnum stateEnum;
                        stateEnum = future.get();
                        if (stateEnum == NodeRunStateEnum.FALSE) {
                            return NodeRunStateEnum.FALSE;
                        }
                        if (!hasTrue) {
                            hasTrue = stateEnum == NodeRunStateEnum.TRUE;
                        }
                        removeFuturePairs.add(pair);
                        size--;
                    }
                }
                if (size > 0) {
                    for (Pair<Long, Future<NodeRunStateEnum>> pair : removeFuturePairs) {
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

        if (hasTrue) {
            return NodeRunStateEnum.TRUE;
        }
        return NodeRunStateEnum.NONE;
    }
}

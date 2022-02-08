package com.ice.core.relation.parallel;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.IceLinkedList;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author zjn
 * relation ANY
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
    protected NodeRunStateEnum processNode(IceContext cxt) {
        IceLinkedList<BaseNode> children = this.getChildren();
        if (children == null || children.isEmpty()) {
            return NodeRunStateEnum.NONE;
        }
        LinkedList<Pair<Long, Future<NodeRunStateEnum>>> futurePairs = new LinkedList<>();
        for (IceLinkedList.Node<BaseNode> listNode = children.getFirst(); listNode != null; listNode = listNode.next) {
            BaseNode node = listNode.item;
            if (node != null) {
                futurePairs.add(new Pair<>(node.findIceNodeId(), IceExecutor.submitNodeCallable(node, cxt)));
            }
        }
        boolean hasFalse = false;
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
                        if (stateEnum == NodeRunStateEnum.TRUE) {
                            return NodeRunStateEnum.TRUE;
                        }
                        if (!hasFalse) {
                            hasFalse = stateEnum == NodeRunStateEnum.FALSE;
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

        if (hasFalse) {
            return NodeRunStateEnum.FALSE;
        }
        return NodeRunStateEnum.NONE;
    }
}

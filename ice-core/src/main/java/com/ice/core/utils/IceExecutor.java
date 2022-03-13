package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceContext;
import com.ice.core.context.IceParallelContext;
import com.ice.core.handler.IceHandler;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * @author zjn
 * IceExecutor
 * used on mutli handler and parallel relation
 */
@Data
@AllArgsConstructor
public final class IceExecutor {

    //used fork join pool to handle the tree model ice parallel execute
    private static ForkJoinPool executor;

    public static void setExecutor(ForkJoinPool executor) {
        if (executor == null) {
            throw new NullPointerException("ice executor can not null");
        }
        IceExecutor.executor = executor;
    }

    public static Future<NodeRunStateEnum> submitNodeCallable(BaseNode node, IceParallelContext cxt) {
        return executor.submit(() -> {
            if (!cxt.isDone()) {
                return node.process(cxt.getCxt());
            }
            return NodeRunStateEnum.NONE;
        });
    }

    public static Future<NodeRunStateEnum> submitNodeCallable(BaseNode node, IceContext cxt) {
        return executor.submit(() -> node.process(cxt));
    }

    public static Future<?> submitNodeRunnable(BaseNode node, IceContext cxt) {
        return executor.submit(() -> {
            node.process(cxt);
        });
    }

    public static Future<?> submitHandler(IceHandler handler, IceContext cxt) {
        return executor.submit(() -> {
            handler.handle(cxt);
        });
    }
}

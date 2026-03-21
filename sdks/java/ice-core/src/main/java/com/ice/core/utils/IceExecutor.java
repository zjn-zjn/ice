package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceRoam;
import com.ice.core.handler.IceHandler;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;

/**
 * @author waitmoon
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

    public static Future<NodeRunStateEnum> submitNodeCallable(BaseNode node, IceRoam roam, boolean[] done) {
        return executor.submit(() -> {
            if (!done[0]) {
                return node.process(roam);
            }
            return NodeRunStateEnum.NONE;
        });
    }

    public static Future<NodeRunStateEnum> submitNodeCallable(BaseNode node, IceRoam roam) {
        return executor.submit(() -> node.process(roam));
    }

    public static Future<?> submitNodeRunnable(BaseNode node, IceRoam roam) {
        return executor.submit(() -> {
            node.process(roam);
        });
    }

    public static Future<IceRoam> submitHandler(IceHandler handler, IceRoam roam) {
        return executor.submit(() -> {
            handler.handle(roam);
            return roam;
        });
    }
}

package com.ice.core;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @author waitmoon
 * ice execution entry
 */
public final class Ice {

    private Ice() {
    }

    /*
     * careless result async exec handler
     */
    public static List<Future<IceContext>> asyncProcess(IcePack pack) {
        return IceDispatcher.asyncDispatcher(pack);
    }

    /*
     * care result sync exec handler
     */
    public static void syncProcess(IcePack pack) {
        IceDispatcher.syncDispatcher(pack);
    }

    /*
     * care result with single roam
     */
    public static IceRoam processSingleRoam(IcePack pack) {
        IceContext ctx = processSingleCtx(pack);
        if (ctx != null && ctx.getPack() != null) {
            return ctx.getPack().getRoam();
        }
        return null;
    }

    /*
     * care result with list roam
     */
    public static List<IceRoam> processRoam(IcePack pack) {
        List<IceContext> ctxList = IceDispatcher.syncDispatcher(pack);
        if (ctxList == null || ctxList.isEmpty()) {
            return null;
        }
        return ctxList.stream().map(ctx -> ctx.getPack().getRoam())
                .collect(Collectors.toCollection(() -> new ArrayList<>(ctxList.size())));
    }

    /*
     * care result with single ctx
     */
    public static IceContext processSingleCtx(IcePack pack) {
        List<IceContext> ctxList = processCtx(pack);
        if (ctxList == null || ctxList.isEmpty()) {
            return null;
        }
        return ctxList.get(0);
    }

    /*
     * care result with single ctx list
     */
    public static List<IceContext> processCtx(IcePack pack) {
        return IceDispatcher.syncDispatcher(pack);
    }
}

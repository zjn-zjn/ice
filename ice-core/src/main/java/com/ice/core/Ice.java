package com.ice.core;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * @author zjn
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
        IceContext cxt = processSingleCxt(pack);
        if (cxt != null && cxt.getPack() != null) {
            return cxt.getPack().getRoam();
        }
        return null;
    }

    /*
     * care result with list roam
     */
    public static List<IceRoam> processRoam(IcePack pack) {
        List<IceContext> cxts = IceDispatcher.syncDispatcher(pack);
        if (cxts == null || cxts.isEmpty()) {
            return null;
        }
        return cxts.stream().map(cxt -> cxt.getPack().getRoam())
                .collect(Collectors.toCollection(() -> new ArrayList<>(cxts.size())));
    }

    /*
     * care result with single cxt
     */
    public static IceContext processSingleCxt(IcePack pack) {
        List<IceContext> cxts = processCxt(pack);
        if (cxts == null || cxts.isEmpty()) {
            return null;
        }
        return cxts.get(0);
    }

    /*
     * care result with single cxt list
     */
    public static List<IceContext> processCxt(IcePack pack) {
        return IceDispatcher.syncDispatcher(pack);
    }
}

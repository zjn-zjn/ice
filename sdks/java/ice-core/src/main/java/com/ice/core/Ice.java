package com.ice.core;

import com.ice.core.context.IceRoam;

import java.util.List;
import java.util.concurrent.Future;

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
    public static List<Future<IceRoam>> asyncProcess(IceRoam roam) {
        return IceDispatcher.asyncDispatcher(roam);
    }

    /*
     * care result sync exec handler
     */
    public static List<IceRoam> syncProcess(IceRoam roam) {
        return IceDispatcher.syncDispatcher(roam);
    }

    /*
     * care result with single roam
     */
    public static IceRoam processSingle(IceRoam roam) {
        List<IceRoam> roamList = IceDispatcher.syncDispatcher(roam);
        if (roamList == null || roamList.isEmpty()) {
            return null;
        }
        return roamList.get(0);
    }

    /*
     * care result with list roam
     */
    public static List<IceRoam> process(IceRoam roam) {
        return IceDispatcher.syncDispatcher(roam);
    }
}

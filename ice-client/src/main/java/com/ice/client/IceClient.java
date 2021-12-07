package com.ice.client;

import com.ice.core.IceDispatcher;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zjn
 */
public final class IceClient {

    private IceClient() {
    }

    /*
     * 不关心结果-handler异步执行
     *
     * @param pack 包裹
     */
    public static void process(IcePack pack) {
        IceDispatcher.asyncDispatcher(pack);
    }

    /*
     * 需要执行后的单个Roam
     *
     * @param pack 包裹
     */
    public static IceRoam processSingleRoam(IcePack pack) {
        IceContext cxt = processSingleCxt(pack);
        if (cxt != null && cxt.getPack() != null) {
            return cxt.getPack().getRoam();
        }
        return null;
    }

    /*
     * 需要执行后的Roam列表
     *
     * @param pack 包裹
     */
    public static List<IceRoam> processRoam(IcePack pack) {
        List<IceContext> cxts = IceDispatcher.syncDispatcher(pack);
        if (CollectionUtils.isEmpty(cxts)) {
            return null;
        }
        return cxts.stream().map(cxt -> cxt.getPack().getRoam())
                .collect(Collectors.toCollection(() -> new ArrayList<>(cxts.size())));
    }

    /*
     * 需要执行后的单个cxt
     *
     * @param pack 包裹
     */
    public static IceContext processSingleCxt(IcePack pack) {
        List<IceContext> cxts = processCxt(pack);
        if (CollectionUtils.isEmpty(cxts)) {
            return null;
        }
        return cxts.get(0);
    }

    /*
     * 需要执行后的cxt列表
     *
     * @param pack 包裹
     */
    public static List<IceContext> processCxt(IcePack pack) {
        return IceDispatcher.syncDispatcher(pack);
    }
}

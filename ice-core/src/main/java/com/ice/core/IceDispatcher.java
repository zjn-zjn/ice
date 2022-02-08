package com.ice.core;

import com.alibaba.fastjson.JSON;
import com.ice.core.base.BaseNode;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.handler.IceHandler;
import com.ice.core.utils.IceExecutor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.Future;

/**
 * @author zjn
 * ice dispatcher with id/scene
 */
@Slf4j
public final class IceDispatcher {

    private IceDispatcher() {
    }

    @SneakyThrows
    public static List<IceContext> syncDispatcher(IcePack pack) {
        if (!checkPack(pack)) {
            return Collections.emptyList();
        }
        /*first iceId*/
        if (pack.getIceId() > 0) {
            IceHandler handler = IceHandlerCache.getHandlerById(pack.getIceId());
            if (handler == null) {
                log.debug("handler maybe expired iceId:{}", pack.getIceId());
                return Collections.emptyList();
            }
            IceContext cxt = new IceContext(handler.findIceId(), pack);
            handler.handle(cxt);
            return Collections.singletonList(cxt);
        }
        /*next scene*/
        if (pack.getScene() != null && !pack.getScene().isEmpty()) {
            Map<Long, IceHandler> handlerMap = IceHandlerCache.getHandlersByScene(pack.getScene());
            if (handlerMap == null || handlerMap.isEmpty()) {
                log.debug("handlers maybe all expired scene:{}", pack.getScene());
                return Collections.emptyList();
            }

            List<IceContext> cxtList = new LinkedList<>();
            if (handlerMap.size() == 1) {
                /*one handler*/
                IceHandler handler = handlerMap.values().iterator().next();
                IceContext cxt = new IceContext(handler.findIceId(), pack);
                handler.handle(cxt);
                cxtList.add(cxt);
                return cxtList;
            }
            /*mutli handler ever each handler roam not conflict(note the effect of roam`s shallow copy)*/
            IceRoam roam = pack.getRoam();
            List<Future<?>> futures = new ArrayList<>(handlerMap.size());
            for (IceHandler handler : handlerMap.values()) {
                IceContext cxt = new IceContext(handler.findIceId(), pack.newPack(roam));
                futures.add(IceExecutor.submitHandler(handler, cxt));
                cxtList.add(cxt);
            }
            for (Future<?> future : futures) {
                future.get();
            }
            return cxtList;
        }

        /*last confId/nodeId*/
        long confId = pack.getConfId();
        if (confId <= 0) {
            return Collections.emptyList();
        }
        BaseNode root = IceConfCache.getConfById(confId);
        if (root != null) {
            IceContext cxt = new IceContext(confId, pack);
            IceHandler handler = new IceHandler();
            handler.setDebug(pack.getDebug());
            handler.setRoot(root);
            handler.setConfId(confId);
            return Collections.singletonList(cxt);
        }
        return Collections.emptyList();
    }

    public static void asyncDispatcher(IcePack pack) {
        if (!checkPack(pack)) {
            return;
        }
        if (pack.getIceId() > 0) {
            IceHandler handler = IceHandlerCache.getHandlerById(pack.getIceId());
            if (handler == null) {
                return;
            }
            IceContext cxt = new IceContext(handler.findIceId(), pack);
            IceExecutor.submitHandler(handler, cxt);
            return;
        }
        if (pack.getScene() != null && !pack.getScene().isEmpty()) {
            Map<Long, IceHandler> handlerMap = IceHandlerCache.getHandlersByScene(pack.getScene());
            if (handlerMap == null || handlerMap.isEmpty()) {
                return;
            }
            if (handlerMap.size() == 1) {
                IceHandler handler = handlerMap.values().iterator().next();
                IceContext cxt = new IceContext(handler.findIceId(), pack);
                IceExecutor.submitHandler(handler, cxt);
                return;
            }
            IceRoam roam = pack.getRoam();
            for (IceHandler handler : handlerMap.values()) {
                IceContext cxt = new IceContext(handler.findIceId(), pack.newPack(roam));
                IceExecutor.submitHandler(handler, cxt);
            }
        }
        long confId = pack.getConfId();
        if (confId <= 0) {
            return;
        }

        BaseNode root = IceConfCache.getConfById(confId);
        if (root != null) {
            IceContext cxt = new IceContext(confId, pack);
            IceHandler handler = new IceHandler();
            handler.setDebug(pack.getDebug());
            handler.setRoot(root);
            handler.setConfId(confId);
            IceExecutor.submitHandler(handler, cxt);
        }
    }

    private static boolean checkPack(IcePack pack) {
        if (pack == null) {
            log.error("invalid pack null");
            return false;
        }
        if (pack.getIceId() > 0) {
            return true;
        }
        if (pack.getScene() != null && !pack.getScene().isEmpty()) {
            return true;
        }
        if (pack.getConfId() > 0) {
            return true;
        }
        log.error("invalid pack none iceId none scene none confId:{}", JSON.toJSONString(pack));
        return false;
    }
}

package com.ice.core;


import com.ice.core.base.BaseNode;
import com.ice.core.cache.IceConfCache;
import com.ice.core.cache.IceHandlerCache;
import com.ice.core.context.IceRoam;
import com.ice.core.handler.IceHandler;
import com.ice.core.utils.IceExecutor;
import com.ice.core.utils.JacksonUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author waitmoon
 * ice dispatcher with id/scene
 */
@Slf4j
public final class IceDispatcher {

    private IceDispatcher() {
    }

    @SneakyThrows
    public static List<IceRoam> syncDispatcher(IceRoam roam) {
        if (!checkRoam(roam)) {
            return Collections.emptyList();
        }
        /*first iceId*/
        if (roam.getId() > 0) {
            IceHandler handler = IceHandlerCache.getHandlerById(roam.getId());
            if (handler == null) {
                log.debug("handler maybe expired iceId:{}", roam.getId());
                return Collections.emptyList();
            }
            roam.setId(handler.findIceId());
            handler.handle(roam);
            return Collections.singletonList(roam);
        }
        /*next scene*/
        String scene = roam.getScene();
        if (scene != null && !scene.isEmpty()) {
            Map<Long, IceHandler> handlerMap = IceHandlerCache.getHandlersByScene(scene);
            if (handlerMap == null || handlerMap.isEmpty()) {
                log.debug("handlers maybe all expired scene:{}", scene);
                return Collections.emptyList();
            }
            if (handlerMap.size() == 1) {
                /*one handler*/
                IceHandler handler = handlerMap.values().iterator().next();
                roam.setId(handler.findIceId());
                handler.handle(roam);
                return Collections.singletonList(roam);
            }
            /*mutli handler ever each handler roam not conflict(note the effect of roam`s shallow copy)*/
            List<IceRoam> roamList = new ArrayList<>(handlerMap.size());
            List<Future<?>> futures = new ArrayList<>(handlerMap.size());
            for (IceHandler handler : handlerMap.values()) {
                IceRoam cloneRoam = roam.cloneRoam();
                cloneRoam.setId(handler.findIceId());
                futures.add(IceExecutor.submitHandler(handler, cloneRoam));
                roamList.add(cloneRoam);
            }
            for (Future<?> future : futures) {
                future.get();
            }
            return roamList;
        }

        /*last node*/
        long confId = roam.getNid();
        if (confId <= 0) {
            return Collections.emptyList();
        }
        BaseNode root = IceConfCache.getConfById(confId);
        if (root != null) {
            roam.setId(confId);
            IceHandler handler = new IceHandler();
            handler.setDebug(roam.getDebug());
            handler.setRoot(root);
            handler.setConfId(confId);
            handler.handle(roam);
            return Collections.singletonList(roam);
        }
        return Collections.emptyList();
    }

    public static List<Future<IceRoam>> asyncDispatcher(IceRoam roam) {
        if (!checkRoam(roam)) {
            return Collections.emptyList();
        }
        if (roam.getId() > 0) {
            IceHandler handler = IceHandlerCache.getHandlerById(roam.getId());
            if (handler == null) {
                return Collections.emptyList();
            }
            roam.setId(handler.findIceId());
            return Collections.singletonList(IceExecutor.submitHandler(handler, roam));
        }
        String scene = roam.getScene();
        if (scene != null && !scene.isEmpty()) {
            Map<Long, IceHandler> handlerMap = IceHandlerCache.getHandlersByScene(scene);
            if (handlerMap == null || handlerMap.isEmpty()) {
                return Collections.emptyList();
            }
            if (handlerMap.size() == 1) {
                IceHandler handler = handlerMap.values().iterator().next();
                roam.setId(handler.findIceId());
                return Collections.singletonList(IceExecutor.submitHandler(handler, roam));
            }
            List<Future<IceRoam>> futures = new ArrayList<>(handlerMap.size());
            for (IceHandler handler : handlerMap.values()) {
                IceRoam cloneRoam = roam.cloneRoam();
                cloneRoam.setId(handler.findIceId());
                futures.add(IceExecutor.submitHandler(handler, cloneRoam));
            }
            return futures;
        }
        long confId = roam.getNid();
        if (confId <= 0) {
            return Collections.emptyList();
        }

        BaseNode root = IceConfCache.getConfById(confId);
        if (root != null) {
            roam.setId(confId);
            IceHandler handler = new IceHandler();
            handler.setDebug(roam.getDebug());
            handler.setRoot(root);
            handler.setConfId(confId);
            return Collections.singletonList(IceExecutor.submitHandler(handler, roam));
        }
        return Collections.emptyList();
    }

    private static boolean checkRoam(IceRoam roam) {
        if (roam == null || roam.getMeta() == null) {
            log.error("invalid roam null or missing _ice");
            return false;
        }
        if (roam.getId() > 0) {
            return true;
        }
        String scene = roam.getScene();
        if (scene != null && !scene.isEmpty()) {
            return true;
        }
        if (roam.getNid() > 0) {
            return true;
        }
        log.error("invalid roam none iceId none scene none confId:{}", JacksonUtils.toJsonString(roam));
        return false;
    }
}

package com.ice.core.cache;

import com.alibaba.fastjson.JSON;
import com.ice.common.dto.IceBaseDto;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.handler.IceHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zjn
 * base config local cache
 * base instance structure update
 */
@Slf4j
public final class IceHandlerCache {

    /*
     * key iceId value handler
     */
    private static final Map<Long, IceHandler> idHandlerMap = new ConcurrentHashMap<>();
    /*
     * key1 scene key2 iceId
     */
    private static final Map<String, Map<Long, IceHandler>> sceneHandlersMap = new ConcurrentHashMap<>();
    /*
     * key1 confId key2 iceId
     */
    private static final Map<Long, Map<Long, IceHandler>> confIdHandlersMap = new ConcurrentHashMap<>();

    private static final String REGEX_COMMA = ",";

    public static IceHandler getHandlerById(Long iceId) {
        return idHandlerMap.get(iceId);
    }

    public static Map<Long, IceHandler> getIdHandlerMap() {
        return idHandlerMap;
    }

    public static Map<Long, IceHandler> getHandlersByScene(String scene) {
        return sceneHandlersMap.get(scene);
    }

    public static List<String> insertOrUpdate(Collection<IceBaseDto> iceBaseDtos) {
        List<String> errors = new ArrayList<>(iceBaseDtos.size());
        for (IceBaseDto base : iceBaseDtos) {
            IceHandler handler = new IceHandler();
            handler.setIceId(base.getId());
            handler.setTimeTypeEnum(TimeTypeEnum.getEnumDefaultNone(base.getTimeType()));
            handler.setStart(base.getStart() == null ? 0 : base.getStart());
            handler.setEnd(base.getEnd() == null ? 0 : base.getEnd());
            Long confId = base.getConfId();
            if (confId != null) {
                /*confId等于空的情况不考虑处理,没配confId的handler是没有意义的*/
                BaseNode root = IceConfCache.getConfById(confId);
                if (root == null) {
                    String errorModeStr = JSON.toJSONString(base);
                    errors.add("confId:" + confId + " not exist conf:" + errorModeStr);
                    log.error("confId:{} not exist please check! conf:{}", confId, errorModeStr);
                    continue;
                }
                Map<Long, IceHandler> handlerMap = confIdHandlersMap.get(confId);
                if (handlerMap == null) {
                    handlerMap = new ConcurrentHashMap<>();
                    confIdHandlersMap.put(confId, handlerMap);
                }
                handlerMap.put(handler.findIceId(), handler);
                handler.setRoot(root);
            }
            handler.setDebug(base.getDebug() == null ? 0 : base.getDebug());
            if (base.getScenes() != null && !base.getScenes().isEmpty()) {
                handler.setScenes(new HashSet<>(Arrays.asList(base.getScenes().split(REGEX_COMMA))));
            } else {
                handler.setScenes(Collections.emptySet());
            }
            onlineOrUpdateHandler(handler);
        }
        return errors;
    }

    public static void updateHandlerRoot(BaseNode confUpdateNode) {
        Map<Long, IceHandler> handlerMap = confIdHandlersMap.get(confUpdateNode.getIceNodeId());
        if (handlerMap != null) {
            for (IceHandler handler : handlerMap.values()) {
                handler.setRoot(confUpdateNode);
            }
        }
    }

    public static void delete(Collection<Long> ids) {
        for (Long id : ids) {
            IceHandler removeHandler = idHandlerMap.get(id);
            if (removeHandler != null && removeHandler.getRoot() != null) {
                confIdHandlersMap.remove(removeHandler.getRoot().getIceNodeId());
            }
            offlineHandler(removeHandler);
        }
    }

    public static void onlineOrUpdateHandler(IceHandler handler) {
        IceHandler originHandler = null;
        if (handler.findIceId() > 0) {
            originHandler = idHandlerMap.get(handler.findIceId());
            idHandlerMap.put(handler.findIceId(), handler);
        }
        /*原有handler的新handler不存在的scene*/
        if (originHandler != null && originHandler.getScenes() != null && !originHandler.getScenes().isEmpty()) {
            if (handler.getScenes() == null || handler.getScenes().isEmpty()) {
                for (String scene : originHandler.getScenes()) {
                    Map<Long, IceHandler> handlerMap = sceneHandlersMap.get(scene);
                    if (handlerMap != null && !handlerMap.isEmpty()) {
                        handlerMap.remove(originHandler.findIceId());
                    }
                    if (handlerMap == null || handlerMap.isEmpty()) {
                        sceneHandlersMap.remove(scene);
                    }
                }
                return;
            }
            for (String scene : originHandler.getScenes()) {
                if (!handler.getScenes().contains(scene)) {
                    /*新的不存在以前的scene*/
                    Map<Long, IceHandler> handlerMap = sceneHandlersMap.get(scene);
                    if (handlerMap != null && !handlerMap.isEmpty()) {
                        handlerMap.remove(originHandler.findIceId());
                    }
                    if (handlerMap == null || handlerMap.isEmpty()) {
                        sceneHandlersMap.remove(scene);
                    }
                }
            }
        }
        for (String scene : handler.getScenes()) {
            Map<Long, IceHandler> handlerMap = sceneHandlersMap.get(scene);
            if (handlerMap == null || handlerMap.isEmpty()) {
                handlerMap = new LinkedHashMap<>();
                sceneHandlersMap.put(scene, handlerMap);
            }
            handlerMap.put(handler.findIceId(), handler);
        }
    }

    private static void offlineHandler(IceHandler handler) {
        if (handler != null) {
            idHandlerMap.remove(handler.findIceId());
            for (String scene : handler.getScenes()) {
                Map<Long, IceHandler> handlerMap = sceneHandlersMap.get(scene);
                if (handlerMap != null && !handlerMap.isEmpty()) {
                    handlerMap.remove(handler.findIceId());
                }
                if (handlerMap == null || handlerMap.isEmpty()) {
                    sceneHandlersMap.remove(scene);
                }
            }
        }
    }

    private static void offlineHandler(Long id) {
        IceHandler handler = idHandlerMap.get(id);
        if (handler != null) {
            offlineHandler(handler);
        }
    }

    /*
     * 下线一个scene下的所有handler
     *
     * @param scene
     */
    private static void offlineHandler(String scene) {
        Map<Long, IceHandler> handlerMap = sceneHandlersMap.get(scene);
        if (handlerMap != null && !handlerMap.isEmpty()) {
            sceneHandlersMap.remove(scene);
        }
    }
}

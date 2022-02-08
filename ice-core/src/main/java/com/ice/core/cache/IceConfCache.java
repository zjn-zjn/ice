package com.ice.core.cache;

import com.alibaba.fastjson.JSON;
import com.ice.common.dto.IceConfDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.relation.*;
import com.ice.core.relation.parallel.*;
import com.ice.core.utils.IceBeanUtils;
import com.ice.core.utils.IceLinkedList;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zjn
 * conf instance structure update
 */
@Slf4j
public final class IceConfCache {

    private static final String REGEX_COMMA = ",";

    private static final Map<Long, BaseNode> confMap = new ConcurrentHashMap<>();

    private static final Map<Long, Set<Long>> parentIdsMap = new ConcurrentHashMap<>();

    private static final Map<Long, Set<Long>> forwardUseIdsMap = new ConcurrentHashMap<>();

    public static BaseNode getConfById(Long id) {
        if (id == null) {
            return null;
        }
        return confMap.get(id);
    }

    public static Map<Long, BaseNode> getConfMap() {
        return confMap;
    }

    /*
     * update local cache
     * @param iceConfDtos dto
     */
    public static List<String> insertOrUpdate(Collection<IceConfDto> iceConfDtos) {
        List<String> errors = new ArrayList<>(iceConfDtos.size());
        Map<Long, BaseNode> tmpConfMap = new HashMap<>(iceConfDtos.size());

        for (IceConfDto confDto : iceConfDtos) {
            try {
                tmpConfMap.put(confDto.getId(), convert(confDto));
            } catch (Exception e) {
                String errorNodeStr = JSON.toJSONString(confDto);
                errors.add("error conf:" + errorNodeStr);
                log.error("ice error conf:{} please check! e:", errorNodeStr, e);
            }
        }
        for (IceConfDto confInfo : iceConfDtos) {
            BaseNode origin = confMap.get(confInfo.getId());
            if (NodeTypeEnum.isRelation(confInfo.getType())) {
                List<Long> sonIds;
                if (confInfo.getSonIds() == null || confInfo.getSonIds().isEmpty()) {
                    sonIds = Collections.emptyList();
                } else {
                    String[] sonIdStrs = confInfo.getSonIds().split(REGEX_COMMA);
                    sonIds = new ArrayList<>();
                    for (String sonStr : sonIdStrs) {
                        sonIds.add(Long.valueOf(sonStr));
                    }
                    for (Long sonId : sonIds) {
                        Set<Long> parentIds = parentIdsMap.get(sonId);
                        if (parentIds == null || parentIds.isEmpty()) {
                            parentIds = new HashSet<>();
                            parentIdsMap.put(sonId, parentIds);
                        }
                        parentIds.add(confInfo.getId());
                        BaseNode tmpNode = tmpConfMap.get(sonId);
                        if (tmpNode == null) {
                            tmpNode = confMap.get(sonId);
                        }
                        if (tmpNode == null) {
                            String errorModeStr = JSON.toJSONString(confInfo);
                            errors.add("sonId:" + sonId + " not exist conf:" + errorModeStr);
                            log.error("sonId:{} not exist please check! conf:{}", sonId, errorModeStr);
                        } else {
                            ((BaseRelation) tmpConfMap.get(confInfo.getId())).getChildren().add(tmpNode);
                        }
                    }
                }
                if (origin instanceof BaseRelation) {
                    BaseRelation originRelation = (BaseRelation) origin;
                    if (originRelation.getChildren() != null && !originRelation.getChildren().isEmpty()) {
                        Set<Long> sonIdSet = new HashSet<>(sonIds);
                        IceLinkedList<BaseNode> children = originRelation.getChildren();
                        IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                        while (listNode != null) {
                            BaseNode sonNode = listNode.item;
                            if (sonNode != null && !sonIdSet.contains(sonNode.findIceNodeId())) {
                                Set<Long> parentIds = parentIdsMap.get(sonNode.findIceNodeId());
                                if (parentIds != null) {
                                    parentIds.remove(originRelation.findIceNodeId());
                                }
                            }
                            listNode = listNode.next;
                        }
                    }
                }
            }
            if (origin != null && origin.getIceForward() != null) {
                if (confInfo.getForwardId() == null || confInfo.getForwardId() != origin.getIceForward()
                        .findIceNodeId()) {
                    Set<Long> forwardUseIds = forwardUseIdsMap.get(origin.getIceForward().findIceNodeId());
                    if (forwardUseIds != null) {
                        forwardUseIds.remove(origin.findIceNodeId());
                    }
                }
            }
            if (confInfo.getForwardId() != null) {
                Set<Long> forwardUseIds = forwardUseIdsMap.get(confInfo.getForwardId());
                if (forwardUseIds == null || forwardUseIds.isEmpty()) {
                    forwardUseIds = new HashSet<>();
                    forwardUseIdsMap.put(confInfo.getForwardId(), forwardUseIds);
                }
                forwardUseIds.add(confInfo.getId());
            }
            if (confInfo.getForwardId() != null) {
                BaseNode tmpForwardNode = tmpConfMap.get(confInfo.getForwardId());
                if (tmpForwardNode == null) {
                    tmpForwardNode = confMap.get(confInfo.getForwardId());
                }
                if (tmpForwardNode == null) {
                    String errorModeStr = JSON.toJSONString(confInfo);
                    errors.add("forwardId:" + confInfo.getForwardId() + " not exist conf:" + errorModeStr);
                    log.error("forwardId:{} not exist please check! conf:{}", confInfo.getForwardId(), errorModeStr);
                } else {
                    tmpConfMap.get(confInfo.getId()).setIceForward(tmpForwardNode);
                }
            }
        }
        confMap.putAll(tmpConfMap);
        for (IceConfDto confInfo : iceConfDtos) {
            Set<Long> parentIds = parentIdsMap.get(confInfo.getId());
            if (parentIds != null && !parentIds.isEmpty()) {
                for (Long parentId : parentIds) {
                    BaseNode tmpParentNode = confMap.get(parentId);
                    if (tmpParentNode == null) {
                        String errorModeStr = JSON.toJSONString(confInfo);
                        errors.add("parentId:" + parentId + " not exist conf:" + errorModeStr);
                        log.error("parentId:{} not exist please check! conf:{}", parentId, errorModeStr);
                    } else {
                        BaseRelation relation = (BaseRelation) tmpParentNode;
                        IceLinkedList<BaseNode> children = relation.getChildren();
                        if (children != null && !children.isEmpty()) {
                            IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                            while (listNode != null) {
                                BaseNode node = listNode.item;
                                if (node != null && node.findIceNodeId() == confInfo.getId()) {
                                    listNode.item = confMap.get(confInfo.getId());
                                }
                                listNode = listNode.next;
                            }
                        }
                    }
                }
            }
            Set<Long> forwardUseIds = forwardUseIdsMap.get(confInfo.getId());
            if (forwardUseIds != null && !forwardUseIds.isEmpty()) {
                for (Long forwardUseId : forwardUseIds) {
                    BaseNode tmpNode = confMap.get(forwardUseId);
                    if (tmpNode == null) {
                        String errorModeStr = JSON.toJSONString(confInfo);
                        errors.add("forwardUseId:" + forwardUseId + " not exist conf:" + errorModeStr);
                        log.error("forwardUseId:{} not exist please check! conf:{}", forwardUseId, errorModeStr);
                    } else {
                        BaseNode forward = confMap.get(confInfo.getId());
                        if (forward != null) {
                            tmpNode.setIceForward(forward);
                        }
                    }
                }
            }
            BaseNode tmpNode = confMap.get(confInfo.getId());
            if (tmpNode != null) {
                IceHandlerCache.updateHandlerRoot(tmpNode);
            }
        }
        return errors;
    }

    public static void delete(Collection<Long> ids) {
        for (Long id : ids) {
            Set<Long> parentIds = parentIdsMap.get(id);
            if (parentIds != null && !parentIds.isEmpty()) {
                for (Long parentId : parentIds) {
                    BaseNode parentNode = confMap.get(parentId);
                    BaseRelation relation = (BaseRelation) parentNode;
                    IceLinkedList<BaseNode> children = relation.getChildren();
                    if (children != null && !children.isEmpty()) {
                        IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                        while (listNode != null) {
                            BaseNode node = listNode.item;
                            if (node != null && node.findIceNodeId() == id) {
                                children.remove(node);
                            }
                            listNode = listNode.next;
                        }
                    }
                }
            }
            confMap.remove(id);
        }
    }

    private static BaseNode convert(IceConfDto confDto) throws ClassNotFoundException {
        BaseNode node;
        switch (NodeTypeEnum.getEnum(confDto.getType())) {
            case LEAF_FLOW:
                String flowFiled = confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField();
                node = (BaseLeafFlow) JSON.parseObject(flowFiled, Class.forName(confDto.getConfName()));
                node.setIceLogName(node.getClass().getSimpleName());
                IceBeanUtils.autowireBean(node);
                break;
            case LEAF_RESULT:
                String resultFiled = confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField();
                node = (BaseLeafResult) JSON.parseObject(resultFiled, Class.forName(confDto.getConfName()));
                node.setIceLogName(node.getClass().getSimpleName());
                IceBeanUtils.autowireBean(node);
                break;
            case LEAF_NONE:
                String noneFiled = confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField();
                node = (BaseLeafNone) JSON.parseObject(noneFiled, Class.forName(confDto.getConfName()));
                node.setIceLogName(node.getClass().getSimpleName());
                IceBeanUtils.autowireBean(node);
                break;
            case NONE:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), None.class);
                node.setIceLogName("None");
                break;
            case AND:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), And.class);
                node.setIceLogName("And");
                break;
            case TRUE:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), True.class);
                node.setIceLogName("True");
                break;
            case ALL:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), All.class);
                node.setIceLogName("All");
                break;
            case ANY:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), Any.class);
                node.setIceLogName("Any");
                break;
            case P_ALL:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), ParallelAll.class);
                node.setIceLogName("P-All");
                break;
            case P_AND:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), ParallelAnd.class);
                node.setIceLogName("P-And");
                break;
            case P_ANY:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), ParallelAny.class);
                node.setIceLogName("P-Any");
                break;
            case P_NONE:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), ParallelNone.class);
                node.setIceLogName("P-None");
                break;
            case P_TRUE:
                node = JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), ParallelTrue.class);
                node.setIceLogName("P-True");
                break;
            default:
                node = (BaseNode) JSON.parseObject(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), Class.forName(confDto.getConfName()));
                if (node != null) {
                    node.setIceLogName(node.getClass().getSimpleName());
                }
                IceBeanUtils.autowireBean(node);
                break;
        }
        node.setIceNodeId(confDto.getId());
        node.setIceNodeDebug(confDto.getDebug() == null || confDto.getDebug() == 1);
        node.setIceInverse(confDto.getInverse() != null && confDto.getInverse());
        node.setIceTimeTypeEnum(TimeTypeEnum.getEnumDefaultNone(confDto.getTimeType()));
        node.setIceStart(confDto.getStart() == null ? 0 : confDto.getStart());
        node.setIceEnd(confDto.getEnd() == null ? 0 : confDto.getEnd());
        return node;
    }
}

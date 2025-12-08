package com.ice.core.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.common.constant.Constant;
import com.ice.common.dto.IceConfDto;
import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.client.IceLeafScanner;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.relation.*;
import com.ice.core.relation.parallel.*;
import com.ice.core.utils.IceBeanUtils;
import com.ice.core.utils.IceLinkedList;
import com.ice.core.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author waitmoon
 * conf instance structure update
 */
@Slf4j
public final class IceConfCache {

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
            } catch (ClassNotFoundException ce) {
                String errorNodeStr = JacksonUtils.toJsonString(confDto);
                errors.add("class not found, conf:" + errorNodeStr);
                log.error("class not found, conf:{}", errorNodeStr);
            } catch (JsonProcessingException je) {
                String errorNodeStr = JacksonUtils.toJsonString(confDto);
                errors.add("json parse error, conf:" + errorNodeStr);
                log.error("json parse error, conf:{}", errorNodeStr);
            } catch (Exception e) {
                String errorNodeStr = JacksonUtils.toJsonString(confDto);
                errors.add("node init error, conf:" + errorNodeStr);
                log.error("node init error, conf:{} e:", errorNodeStr, e);
            }
        }
        for (IceConfDto confInfo : iceConfDtos) {
            BaseNode origin = confMap.get(confInfo.getId());
            if (NodeTypeEnum.isRelation(confInfo.getType())) {
                List<Long> sonIds;
                if (confInfo.getSonIds() == null || confInfo.getSonIds().isEmpty()) {
                    sonIds = Collections.emptyList();
                } else {
                    BaseRelation relation = (BaseRelation) tmpConfMap.get(confInfo.getId());
                    String[] sonIdStrs = confInfo.getSonIds().split(Constant.REGEX_COMMA);
                    sonIds = new ArrayList<>(sonIdStrs.length);
                    for (String sonStr : sonIdStrs) {
                        sonIds.add(Long.valueOf(sonStr));
                    }
                    relation.setIceSonIds(sonIds);
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
                            String errorModeStr = JacksonUtils.toJsonString(confInfo);
                            errors.add("sonId:" + sonId + " not exist conf:" + errorModeStr);
                            log.error("sonId:{} not exist please check! conf:{}", sonId, errorModeStr);
                        } else {
                            relation.getIceChildren().add(tmpNode);
                        }
                    }
                }
                if (origin instanceof BaseRelation) {
                    BaseRelation originRelation = (BaseRelation) origin;
                    if (originRelation.getIceChildren() != null && !originRelation.getIceChildren().isEmpty()) {
                        Set<Long> sonIdSet = new HashSet<>(sonIds);
                        IceLinkedList<BaseNode> children = originRelation.getIceChildren();
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
            } else {
                //origin is relation node now not
                if (origin instanceof BaseRelation) {
                    BaseRelation originRelation = (BaseRelation) origin;
                    if (originRelation.getIceChildren() != null && !originRelation.getIceChildren().isEmpty()) {
                        IceLinkedList<BaseNode> children = originRelation.getIceChildren();
                        IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                        while (listNode != null) {
                            BaseNode sonNode = listNode.item;
                            if (sonNode != null) {
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
            if (confInfo.getForwardId() != null && confInfo.getForwardId() > 0) {
                Set<Long> forwardUseIds = forwardUseIdsMap.get(confInfo.getForwardId());
                if (forwardUseIds == null || forwardUseIds.isEmpty()) {
                    forwardUseIds = new HashSet<>();
                    forwardUseIdsMap.put(confInfo.getForwardId(), forwardUseIds);
                }
                forwardUseIds.add(confInfo.getId());
                BaseNode tmpForwardNode = tmpConfMap.get(confInfo.getForwardId());
                if (tmpForwardNode == null) {
                    tmpForwardNode = confMap.get(confInfo.getForwardId());
                }
                if (tmpForwardNode == null) {
                    String errorModeStr = JacksonUtils.toJsonString(confInfo);
                    errors.add("forwardId:" + confInfo.getForwardId() + " not exist, conf:" + errorModeStr);
                    log.error("forwardId:{} not exist please check, conf:{}", confInfo.getForwardId(), errorModeStr);
                } else {
                    BaseNode nextNode = tmpConfMap.get(confInfo.getId());
                    if (nextNode != null) {
                        nextNode.setIceForward(tmpForwardNode);
                    }
                }
            }
        }
        confMap.putAll(tmpConfMap);
        for (IceConfDto confInfo : iceConfDtos) {
            Set<Long> parentIds = parentIdsMap.get(confInfo.getId());
            Set<Long> removeParentIds = new HashSet<>();
            if (parentIds != null && !parentIds.isEmpty()) {
                for (Long parentId : parentIds) {
                    BaseNode tmpParentNode = confMap.get(parentId);
                    if (tmpParentNode == null) {
                        String errorModeStr = JacksonUtils.toJsonString(confInfo);
                        errors.add("parentId:" + parentId + " not exist, conf:" + errorModeStr);
                        log.error("parentId:{} not exist please check, conf:{}", parentId, errorModeStr);
                    } else {
                        if (tmpParentNode instanceof BaseRelation) {
                            BaseRelation relation = (BaseRelation) tmpParentNode;
                            List<Long> sonIds = relation.getIceSonIds();
                            IceLinkedList<BaseNode> children = new IceLinkedList<>();
                            if (sonIds != null && !sonIds.isEmpty()) {
                                for (Long sonId : relation.getIceSonIds()) {
                                    BaseNode child = confMap.get(sonId);
                                    if (child != null) {
                                        children.add(child);
                                    }
                                }
                            }
                            relation.setIceChildren(children);
                        } else {
                            //parent are not relation node
                            removeParentIds.add(parentId);
                        }
                    }
                }
                parentIds.removeAll(removeParentIds);
            }
            Set<Long> forwardUseIds = forwardUseIdsMap.get(confInfo.getId());
            if (forwardUseIds != null && !forwardUseIds.isEmpty()) {
                for (Long forwardUseId : forwardUseIds) {
                    BaseNode tmpNode = confMap.get(forwardUseId);
                    if (tmpNode == null) {
                        String errorModeStr = JacksonUtils.toJsonString(confInfo);
                        errors.add("forwardUseId:" + forwardUseId + " not exist, conf:" + errorModeStr);
                        log.error("forwardUseId:{} not exist please check, conf:{}", forwardUseId, errorModeStr);
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
            confMap.remove(id);
        }
    }

    private static BaseNode convert(IceConfDto confDto) throws ClassNotFoundException, JsonProcessingException {
        BaseNode node;
        // Resolve alias mapping for multi-language compatibility
        String confName = IceLeafScanner.resolveClassName(confDto.getConfName());
        switch (NodeTypeEnum.getEnum(confDto.getType())) {
            case LEAF_FLOW:
                String flowFiled = confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField();
                node = (BaseLeafFlow) JacksonUtils.readJson(flowFiled, Class.forName(confName, true, IceLeafScanner.getClassLoader()));
                if (node.getIceLogName() == null) {
                    node.setIceLogName(node.getClass().getSimpleName());
                }
                assembleBasicInfo(node, confDto);
                IceBeanUtils.autowireBean(node);
                node.afterPropertiesSet();
                break;
            case LEAF_RESULT:
                String resultFiled = confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField();
                node = (BaseLeafResult) JacksonUtils.readJson(resultFiled, Class.forName(confName, true, IceLeafScanner.getClassLoader()));
                if (node.getIceLogName() == null) {
                    node.setIceLogName(node.getClass().getSimpleName());
                }
                assembleBasicInfo(node, confDto);
                IceBeanUtils.autowireBean(node);
                node.afterPropertiesSet();
                break;
            case LEAF_NONE:
                String noneFiled = confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField();
                node = (BaseLeafNone) JacksonUtils.readJson(noneFiled, Class.forName(confName, true, IceLeafScanner.getClassLoader()));
                if (node.getIceLogName() == null) {
                    node.setIceLogName(node.getClass().getSimpleName());
                }
                assembleBasicInfo(node, confDto);
                IceBeanUtils.autowireBean(node);
                node.afterPropertiesSet();
                break;
            case NONE:
                node = new None();
                node.setIceLogName("None");
                assembleBasicInfo(node, confDto);
                break;
            case AND:
                node = new And();
                node.setIceLogName("And");
                assembleBasicInfo(node, confDto);
                break;
            case TRUE:
                node = new True();
                node.setIceLogName("True");
                assembleBasicInfo(node, confDto);
                break;
            case ALL:
                node = new All();
                node.setIceLogName("All");
                assembleBasicInfo(node, confDto);
                break;
            case ANY:
                node = new Any();
                node.setIceLogName("Any");
                assembleBasicInfo(node, confDto);
                break;
            case P_ALL:
                node = new ParallelAll();
                node.setIceLogName("P-All");
                assembleBasicInfo(node, confDto);
                break;
            case P_AND:
                node = new ParallelAnd();
                node.setIceLogName("P-And");
                assembleBasicInfo(node, confDto);
                break;
            case P_ANY:
                node = new ParallelAny();
                node.setIceLogName("P-Any");
                break;
            case P_NONE:
                node = new ParallelNone();
                node.setIceLogName("P-None");
                assembleBasicInfo(node, confDto);
                break;
            case P_TRUE:
                node = new ParallelTrue();
                node.setIceLogName("P-True");
                assembleBasicInfo(node, confDto);
                break;
            default:
                node = (BaseNode) JacksonUtils.readJson(confDto.getConfField() == null || confDto.getConfField().isEmpty() ? "{}" :
                        confDto.getConfField(), Class.forName(confName, true, IceLeafScanner.getClassLoader()));
                if (node != null && node.getIceLogName() == null) {
                    node.setIceLogName(node.getClass().getSimpleName());
                    assembleBasicInfo(node, confDto);
                    IceBeanUtils.autowireBean(node);
                    node.afterPropertiesSet();
                }
                break;
        }
        return node;
    }

    private static void assembleBasicInfo(BaseNode node, IceConfDto confDto) {
        node.setIceNodeId(confDto.getId());
        node.setIceNodeDebug(confDto.getDebug() == null || confDto.getDebug() == 1);
        node.setIceInverse(confDto.getInverse() != null && confDto.getInverse());
        node.setIceTimeTypeEnum(TimeTypeEnum.getEnumDefaultNone(confDto.getTimeType()));
        node.setIceStart(confDto.getStart() == null ? 0 : confDto.getStart());
        node.setIceEnd(confDto.getEnd() == null ? 0 : confDto.getEnd());
        node.setIceErrorStateEnum(NodeRunStateEnum.getEnumDefaultShutdown(confDto.getErrorState()));
        node.setIceType(confDto.getType());
    }
}

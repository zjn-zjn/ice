package com.ice.core.client;


import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.common.model.Pair;
import com.ice.core.Ice;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.cache.IceConfCache;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.utils.IceLinkedList;
import com.ice.core.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;


/**
 * @author waitmoon
 */
@Slf4j
public final class IceNioClientService {

    /**
     * when server add new leaf node, check the node exist on client
     *
     * @param clazz   server add new leaf class
     * @param type    leaf type
     * @param address address
     * @return result of check
     */
    public static Pair<Integer, String> confClazzCheck(String clazz, byte type, String address) {
        try {
            Class<?> clientClazz = Class.forName(clazz);
            if (Modifier.isAbstract(clientClazz.getModifiers())) {
                return new Pair<>(0, "class is abstract in " + address + " input(" + clazz + "|" + type + ")");
            }
            NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(type);
            boolean res = false;
            switch (typeEnum) {
                case LEAF_FLOW:
                    res = BaseLeafFlow.class.isAssignableFrom(clientClazz);
                    break;
                case LEAF_NONE:
                    res = BaseLeafNone.class.isAssignableFrom(clientClazz);
                    break;
                case LEAF_RESULT:
                    res = BaseLeafResult.class.isAssignableFrom(clientClazz);
                    break;
            }
            if (res) {
                return new Pair<>(1, null);
            } else {
                return new Pair<>(0, "type not match in " + address + " input(" + clazz + "|" + type + ")");
            }
        } catch (ClassNotFoundException e) {
            return new Pair<>(0, "class not found in " + address + " input(" + clazz + "|" + type + ")");
        } catch (Exception e) {
            return new Pair<>(0, address);
        }
    }

    /**
     * update when server release new config
     *
     * @param dto update info
     * @return errors of base/node on instantiate
     */
    public static List<String> update(IceTransferDto dto) {
        List<String> results = new ArrayList<>();
        try {
            log.info("ice update start dto:{}", JacksonUtils.toJsonString(dto));
            List<String> errors = IceUpdate.update(dto);
            if (!errors.isEmpty()) {
                results.addAll(errors);
            }
            log.info("ice update end success");
        } catch (Exception e) {
            log.error("ice update error dto:{} e:", JacksonUtils.toJsonString(dto), e);
        }
        return results;
    }

    /**
     * get the real instantiated client
     *
     * @param confId  the root node id
     * @param address address
     * @return result of config
     */
    public static IceShowConf getShowConf(Long confId, String address) {
        IceShowConf clientConf = new IceShowConf();
        clientConf.setAddress(address);
        clientConf.setConfId(confId);
        BaseNode node = IceConfCache.getConfById(confId);
        if (node != null) {
            clientConf.setRoot(assembleShowNode(node));
        }
        return clientConf;
    }

    private static IceShowNode assembleShowNode(BaseNode node) {
        if (node == null) {
            return null;
        }
        IceShowNode clientNode = new IceShowNode();
        if (node instanceof BaseRelation) {
            BaseRelation relation = (BaseRelation) node;
            IceLinkedList<BaseNode> children = relation.getChildren();
            if (children != null && !children.isEmpty()) {
                List<IceShowNode> showChildren = new ArrayList<>(children.getSize());
                for (IceLinkedList.Node<BaseNode> listNode = children.getFirst();
                     listNode != null; listNode = listNode.next) {
                    BaseNode child = listNode.item;
                    IceShowNode childMap = assembleShowNode(child);
                    if (childMap != null) {
                        showChildren.add(childMap);
                    }
                }
                clientNode.setChildren(showChildren);
            }

        }
        BaseNode forward = node.getIceForward();
        if (forward != null) {
            IceShowNode forwardNode = assembleShowNode(forward);
            if (forwardNode != null) {
                clientNode.setForward(forwardNode);
            }
        }
        IceShowNode.NodeConf showConf = new IceShowNode.NodeConf();
        clientNode.setShowConf(showConf);
        showConf.setNodeId(node.getIceNodeId());
        clientNode.setTimeType(node.getIceTimeTypeEnum().getType());
        clientNode.setStart(node.getIceStart() == 0 ? null : node.getIceStart());
        clientNode.setEnd(node.getIceEnd() == 0 ? null : node.getIceEnd());
        showConf.setDebug(node.isIceNodeDebug() ? null : node.isIceNodeDebug());
        showConf.setInverse(node.isIceInverse() ? node.isIceInverse() : null);
        if (node.getIceErrorStateEnum() != null && node.getIceErrorStateEnum() != NodeRunStateEnum.SHUT_DOWN) {
            showConf.setErrorState(node.getIceErrorStateEnum().getState());
        }
        showConf.setConfField(JacksonUtils.toJsonStringWithoutIceBean(node));
        return clientNode;
    }

    /**
     * mock data when you need
     *
     * @param pack request pack
     * @return result of client process ctx
     */
    public static List<IceContext> mock(IcePack pack) {
        return Ice.processCtx(pack);
    }
}

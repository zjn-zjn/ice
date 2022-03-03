package com.ice.client.rmi;

import com.alibaba.fastjson.JSON;
import com.ice.client.IceClient;
import com.ice.client.change.IceUpdate;
import com.ice.client.config.IceClientProperties;
import com.ice.client.utils.AddressUtils;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.core.base.BaseNode;
import com.ice.core.base.BaseRelation;
import com.ice.core.cache.IceConfCache;
import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.relation.*;
import com.ice.core.utils.IceLinkedList;
import com.ice.rmi.common.client.IceRmiClientService;
import com.ice.common.model.Pair;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class IceRmiClientServiceImpl implements IceRmiClientService {

    @Resource
    private IceClientProperties properties;

    private static volatile boolean waitInit = true;

    private static volatile long initVersion;

    private List<IceTransferDto> waitMessageList = new ArrayList<>();

    public static void initEnd(long version) {
        waitInit = false;
        initVersion = version;
    }

    @Override
    public Pair<Integer, String> confClazzCheck(String clazz, byte type) throws RemoteException {
        try {
            Class<?> clientClazz = Class.forName(clazz);
            NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(type);
            boolean res = false;
            switch (typeEnum) {
                case ALL:
                    res = All.class.isAssignableFrom(clientClazz);
                    break;
                case AND:
                    res = And.class.isAssignableFrom(clientClazz);
                    break;
                case NONE:
                    res = None.class.isAssignableFrom(clientClazz);
                    break;
                case TRUE:
                    res = True.class.isAssignableFrom(clientClazz);
                    break;
                case ANY:
                    res = Any.class.isAssignableFrom(clientClazz);
                    break;
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
                return new Pair<>(0, "type not match in " + AddressUtils.getAddress() + " input(" + clazz + "|" + type + ")");
            }
        } catch (ClassNotFoundException e) {
            return new Pair<>(0, "class not found in " + AddressUtils.getAddress() + " input(" + clazz + "|" + type + ")");
        } catch (Exception e) {
            return new Pair<>(0, AddressUtils.getAddress());
        }
    }

    @Override
    public List<String> update(IceTransferDto dto) throws RemoteException {
        try {
            if (waitInit) {
                log.info("wait init message:{}", JSON.toJSONString(dto));
                waitMessageList.add(dto);
                return null;
            }
            if (!CollectionUtils.isEmpty(waitMessageList)) {
                for (IceTransferDto transferDto : waitMessageList) {
                    handleBeforeInitMessage(transferDto);
                }
                waitMessageList = null;
            }
            handleMessage(dto);
        } catch (Exception e) {
            log.error("ice listener update error message:{} e:", JSON.toJSONString(dto), e);
        }
        return Collections.emptyList();
    }

    private void handleBeforeInitMessage(IceTransferDto dto) {
        if (dto.getVersion() > initVersion) {
            log.info("ice listener update wait msg iceStart iceInfo:{}", dto);
            IceUpdate.update(dto);
            log.info("ice listener update wait msg iceEnd success");
            return;
        }
        log.info("ice listener msg version low then init version:{}, msg:{}", initVersion, JSON.toJSONString(dto));
    }

    private void handleMessage(IceTransferDto dto) {
        log.info("ice listener update msg iceStart dto:{}", JSON.toJSONString(dto));
        IceUpdate.update(dto);
        log.info("ice listener update msg iceEnd success");
    }

    @Override
    public IceShowConf getShowConf(Long confId) throws RemoteException {
        IceShowConf clientConf = new IceShowConf();
        clientConf.setAddress(AddressUtils.getAddress());
        clientConf.setApp(properties.getApp());
        clientConf.setConfId(confId);
        BaseNode node = IceConfCache.getConfById(confId);
        if (node != null) {
            clientConf.setRoot(assembleShowNode(node));
        }
        return clientConf;
    }

    private IceShowNode assembleShowNode(BaseNode node) {
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
        return clientNode;
    }

    @Override
    public List<IceContext> mock(IcePack pack) throws RemoteException {
        return IceClient.processCxt(pack);
    }

    @Override
    public void ping() throws RemoteException {
    }
}

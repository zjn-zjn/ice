package com.ice.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONValidator;
import com.ice.common.constant.Constant;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.IceClientConf;
import com.ice.common.model.IceClientNode;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.model.IceConf;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceLeafClass;
import com.ice.server.model.ServerConstant;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class IceConfServiceImpl implements IceConfService {

    @Resource
    private IceConfMapper iceConfMapper;

    @Resource
    private IceServerService iceServerService;

    @Resource
    private AmqpTemplate amqpTemplate;

    @Override
    @Transactional
    public Long confEdit(IceConf conf, Long parentId, Long nextId) {
        conf.setUpdateAt(new Date());
        if (StringUtils.hasLength(conf.getConfField())) {
            JSONValidator validator = JSONValidator.from(conf.getConfField());
            if (!validator.validate()) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "confFiled json illegal");
            }
        }
        if (conf.getId() == null && (parentId == null || nextId == null)) {
            throw new ErrorCodeException(ErrorCode.CAN_NOT_NULL, "parentId or nextId");
        }
        if (conf.getId() == null) {
            NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(conf.getType());
            if (typeEnum == null) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "nodeType");
            }
            if (conf.getApp() == null) {
                throw new ErrorCodeException(ErrorCode.CAN_NOT_NULL, "app");
            }
            if (ServerConstant.isLeaf(conf.getType())) {
                leafClassCheck(conf.getApp(), conf.getConfName(), conf.getType());
            }
            if (parentId != null) {
                /*add son*/
                IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
                if (parent == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
                }
                iceConfMapper.insertSelective(conf);
                Long id = conf.getId();
                if (!StringUtils.hasLength(parent.getSonIds())) {
                    parent.setSonIds(id + "");
                } else {
                    parent.setSonIds(parent.getSonIds() + "," + id);
                }
                parent.setUpdateAt(new Date());
                iceConfMapper.updateByPrimaryKeySelective(parent);
                return id;
            }
            if (nextId != null) {
                /*add forward*/
                IceConf next = iceConfMapper.selectByPrimaryKey(nextId);
                if (next == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", nextId);
                }
                if (next.getForwardId() != null && next.getForwardId() > 0) {
                    throw new ErrorCodeException(ErrorCode.ALREADY_EXIST, "nextId:" + nextId + " forward");
                }
                iceConfMapper.insertSelective(conf);
                Long id = conf.getId();
                next.setForwardId(id);
                next.setUpdateAt(new Date());
                iceConfMapper.updateByPrimaryKeySelective(next);
                return id;
            }
        }
        IceConf oldConf = iceConfMapper.selectByPrimaryKey(conf.getId());
        if (oldConf == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "confId", conf.getId());
        }
        Byte type = conf.getType() == null ? oldConf.getType() : conf.getType();
        if (ServerConstant.isLeaf(type)) {
            String clazz = StringUtils.hasLength(conf.getConfName()) ? conf.getConfName() : oldConf.getConfName();
            if (!(type.equals(oldConf.getType()) && clazz.equals(oldConf.getConfName()))) {
                leafClassCheck(oldConf.getApp(), clazz, type);
            }
        }
        iceConfMapper.updateByPrimaryKeySelective(conf);
        return conf.getId();
    }

    @Override
    public List<IceLeafClass> getConfLeafClass(Integer app, Byte type) {
        List<IceLeafClass> list = new ArrayList<>();
        Map<String, Integer> leafClassMap = iceServerService.getLeafClassMap(app, type);
        if (leafClassMap != null) {
            for (Map.Entry<String, Integer> entry : leafClassMap.entrySet()) {
                IceLeafClass leafClass = new IceLeafClass();
                leafClass.setFullName(entry.getKey());
                leafClass.setCount(entry.getValue());
                leafClass.setShortName(entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1));
                list.add(leafClass);
            }
        }
        list.sort(Comparator.comparingInt(IceLeafClass::sortNegativeCount));
        return list;
    }

    @Override
    public String leafClassCheck(Integer app, String clazz, Byte type) {
        NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(type);
        if (app == null || !StringUtils.hasLength(clazz) || typeEnum == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR);
        }
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getConfExchange(), String.valueOf(app), clazz + "," + type);
        if (obj == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app);
        }
        String resStr = (String) obj;
        if (!StringUtils.hasLength(resStr)) {
            throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app);
        }
        String[] res = resStr.split(",");
        if ("1".equals(res[0])) {
            return res[1];
        }
        throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app, res[1]);
    }

    @Override
    public IceClientConf confDetail(Integer app, Long confId) {
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getConfExchange(), String.valueOf(app),
                String.valueOf(confId));
        if (obj == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, null);
        }
        String json = (String) obj;
        if (!StringUtils.hasLength(json)) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, null);
        }
        IceClientConf clientConf = JSON.parseObject(json, IceClientConf.class);
        IceClientNode node = clientConf.getNode();
        if (node == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, JSON.toJSONString(clientConf));
        }
        assemble(app, node);
        return clientConf;
    }

    private void assemble(Integer app, IceClientNode clientNode) {
        if (clientNode == null) {
            return;
        }
        Long nodeId = clientNode.getIceNodeId();
        IceClientNode forward = clientNode.getIceForward();
        if (forward != null) {
            forward.setNextId(nodeId);
        }
        assembleInfoInServer(app, clientNode);
        assemble(app, forward);
        List<IceClientNode> children = clientNode.getChildren();
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (IceClientNode child : children) {
            child.setParentId(nodeId);
            assemble(app, child);
        }
    }

    private void assembleInfoInServer(Integer app, IceClientNode clientNode) {
        Long nodeId = clientNode.getIceNodeId();
        IceConf iceConf = iceServerService.getActiveConfById(app, nodeId);
        if (iceConf != null) {
            if (StringUtils.hasLength(iceConf.getName())) {
                clientNode.setNodeName(iceConf.getName());
            }
            if (StringUtils.hasLength(iceConf.getConfField())) {
                clientNode.setConfField(iceConf.getConfField());
            }
            if (StringUtils.hasLength(iceConf.getConfName())) {
                clientNode.setConfName(iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1));
            }
            if (iceConf.getType() != null) {
                clientNode.setNodeType(iceConf.getType());
            }
        }
    }
}

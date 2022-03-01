package com.ice.server.service.impl;

import com.alibaba.fastjson.JSONValidator;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.StatusEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.model.IceConf;
import com.ice.server.dao.model.IceConfExample;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceLeafClass;
import com.ice.server.rmi.IceRmiClientManager;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;
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
    private IceRmiClientManager rmiClientManager;

    @Override
    @Transactional
    public Long confEdit(Integer app, IceConf conf) {
        if (app == null || conf.getMixId() == null) {
            throw new ErrorCodeException(ErrorCode.CAN_NOT_NULL, "app|conf.id");
        }
        conf.setApp(app);
        paramHandle(conf);
        iceConfMapper.updateByPrimaryKey(conf);
        return conf.getMixId();
    }

    @Override
    @Transactional
    public Long confAddSon(Integer app, IceConf conf, Long parentId) {
        conf.setId(null);
        conf.setApp(app);
        paramHandle(conf);
        IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
        if (parent == null || !parent.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
        }
        iceConfMapper.insertSelective(conf);
        Long id = conf.getMixId();
        if (!StringUtils.hasLength(parent.getSonIds())) {
            parent.setSonIds(id + "");
        } else {
            parent.setSonIds(parent.getSonIds() + "," + id);
        }
        parent.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKeySelective(parent);
        return id;
    }

    @Override
    @Transactional
    public List<Long> confAddSonIds(Integer app, String sonIds, Long parentId) {
        String[] sonIdStrs = sonIds.split(",");
        Set<Long> sonIdSet = new HashSet<>(sonIdStrs.length);
        List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
        for (String sonIdStr : sonIdStrs) {
            Long sonId = Long.valueOf(sonIdStr);
            sonIdSet.add(sonId);
            sonIdList.add(sonId);
        }
        if (iceServerService.haveCircle(parentId, sonIdList)) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check sonIds");
        }
        IceConfExample example = new IceConfExample();
        example.createCriteria().andAppEqualTo(app).andIdIn(sonIdSet);
        List<IceConf> children = iceConfMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(children) || children.size() != sonIdSet.size()) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "sonIds", sonIds);
        }
        IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
        if (parent == null || !parent.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
        }
        if (!StringUtils.hasLength(parent.getSonIds())) {
            parent.setSonIds(sonIds);
        } else {
            parent.setSonIds(parent.getSonIds() + "," + sonIds);
        }
        parent.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKey(parent);
        return sonIdList;
    }

    @Override
    @Transactional
    public Long confAddForward(Integer app, IceConf conf, Long nextId) {
        conf.setId(null);
        conf.setApp(app);
        paramHandle(conf);
        IceConf next = iceConfMapper.selectByPrimaryKey(nextId);
        if (next == null || !next.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", nextId);
        }
        if (next.getForwardId() != null) {
            throw new ErrorCodeException(ErrorCode.ALREADY_EXIST, "forward");
        }
        iceConfMapper.insertSelective(conf);
        next.setForwardId(conf.getMixId());
        next.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKey(next);
        return conf.getMixId();
    }

    @Override
    @Transactional
    public Long confAddForwardId(Integer app, Long forwardId, Long nextId) {
        if (iceServerService.haveCircle(nextId, forwardId)) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check forwardId");
        }
        IceConf forward = iceConfMapper.selectByPrimaryKey(forwardId);
        if (forward == null || !forward.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "forwardId", forwardId);
        }
        IceConf next = iceConfMapper.selectByPrimaryKey(nextId);
        if (next == null || !next.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", nextId);
        }
        if (next.getForwardId() != null) {
            throw new ErrorCodeException(ErrorCode.ALREADY_EXIST, "forward");
        }
        next.setForwardId(forwardId);
        next.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKey(next);
        return nextId;
    }

    @Override
    @Transactional
    public Long confEditId(Integer app, Long nodeId, Long exchangeId, Long parentId, Long nextId, Integer index) {
        IceConf exchange = iceConfMapper.selectByPrimaryKey(exchangeId);
        if (exchange == null || !exchange.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "exchangeId", exchangeId);
        }
        if (parentId != null) {
            if (iceServerService.haveCircle(parentId, exchangeId)) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check exchangeId");
            }
            IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
            if (parent == null || !parent.getApp().equals(app)) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
            }
            if (!StringUtils.hasLength(parent.getSonIds())) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son");
            }
            String[] sonIdStrs = parent.getSonIds().split(",");
            if (index == null || index < 0 || index >= sonIdStrs.length || !sonIdStrs[index].equals(nodeId + "")) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
            }
            sonIdStrs[index] = exchangeId + "";
            StringBuilder sb = new StringBuilder();
            for (String idStr : sonIdStrs) {
                sb.append(idStr).append(",");
            }
            String str = sb.toString();
            parent.setSonIds(str.substring(0, str.length() - 1));
            parent.setUpdateAt(new Date());
            iceConfMapper.updateByPrimaryKey(parent);
            return parentId;
        }
        if (nextId != null) {
            if (iceServerService.haveCircle(nextId, exchangeId)) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check exchangeId");
            }
            IceConf next = iceConfMapper.selectByPrimaryKey(nextId);
            if (next == null || !next.getApp().equals(app)) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", nextId);
            }
            if (!nodeId.equals(next.getForwardId())) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "node is not the next forward");
            }
            next.setForwardId(exchangeId);
            next.setUpdateAt(new Date());
            iceConfMapper.updateByPrimaryKey(next);
            return nextId;
        }
        throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "root can not change id");
    }

    @Override
    @Transactional
    public Long confForwardDelete(Integer app, Long forwardId, Long nextId) {
        IceConf next = iceConfMapper.selectByPrimaryKey(nextId);
        if (next == null || !next.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", nextId);
        }
        if (!forwardId.equals(next.getForwardId())) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "node is not the next forward");
        }
        next.setForwardId(null);
        next.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKey(next);
        return nextId;
    }

    @Override
    @Transactional
    public Long confSonDelete(Integer app, Long sonId, Long parentId, Integer index) {
        IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
        if (parent == null || !parent.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
        }
        if (!StringUtils.hasLength(parent.getSonIds())) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son");
        }
        String[] sonIdStrs = parent.getSonIds().split(",");
        if (index == null || index < 0 || index >= sonIdStrs.length || !sonIdStrs[index].equals(sonId + "")) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
        }
        sonIdStrs[index] = null;
        StringBuilder sb = new StringBuilder();
        for (String idStr : sonIdStrs) {
            if (idStr != null) {
                sb.append(idStr).append(",");
            }
        }
        String str = sb.toString();
        if (StringUtils.hasLength(str)) {
            parent.setSonIds(str.substring(0, str.length() - 1));
        } else {
            parent.setSonIds(null);
        }
        parent.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKey(parent);
        return parentId;
    }

    @Override
    @Transactional
    public Long confSonMove(Integer app, Long parentId, Long sonId, Integer originIndex, Integer toIndex) {
        if (originIndex == null || toIndex == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "originIndex|toIndex");
        }
        if (originIndex.equals(toIndex)) {
            return parentId;
        }
        IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
        if (parent == null || !parent.getApp().equals(app)) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
        }
        if (!StringUtils.hasLength(parent.getSonIds())) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son");
        }
        String[] sonIdStrs = parent.getSonIds().split(",");
        String sonIdStr = sonId + "";
        if (originIndex < 0 || originIndex >= sonIdStrs.length || !sonIdStrs[originIndex].equals(sonIdStr)) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input origin index");
        }
        if (toIndex < 0 || toIndex >= sonIdStrs.length) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "input to index illegal");
        }
        sonIdStrs[originIndex] = sonIdStrs[toIndex];
        sonIdStrs[toIndex] = sonIdStr;
        StringBuilder sb = new StringBuilder();
        for (String idStr : sonIdStrs) {
            if (idStr != null) {
                sb.append(idStr).append(",");
            }
        }
        String str = sb.toString();
        parent.setSonIds(str.substring(0, str.length() - 1));
        parent.setUpdateAt(new Date());
        iceConfMapper.updateByPrimaryKey(parent);
        return parentId;
    }

    private void paramHandle(IceConf conf) {
        conf.setStatus((byte) 1);
        if (conf.getApp() == null || conf.getType() == null || NodeTypeEnum.getEnum(conf.getType()) == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app or type");
        }
        if (StringUtils.hasLength(conf.getConfField())) {
            JSONValidator validator = JSONValidator.from(conf.getConfField());
            if (!validator.validate()) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "confFiled json illegal");
            }
        }
        if (conf.getMixId() == null) {
            if (NodeTypeEnum.isLeaf(conf.getType())) {
                leafClassCheck(conf.getApp(), conf.getConfName(), conf.getType());
            }
        } else {
            IceConf oldConf = iceConfMapper.selectByPrimaryKey(conf.getMixId());
            if (oldConf == null || StatusEnum.OFFLINE.getStatus() == oldConf.getStatus() || !oldConf.getApp().equals(conf.getApp())) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "confId", conf.getMixId());
            }
            /*can not edit sonIds in here*/
            conf.setSonIds(oldConf.getSonIds());
            Byte type = conf.getType();
            if (NodeTypeEnum.isLeaf(type)) {
                String clazz = conf.getConfName();
                if (!(type.equals(oldConf.getType()) && clazz.equals(oldConf.getConfName()))) {
                    leafClassCheck(oldConf.getApp(), clazz, type);
                }
            }
        }
        conf.setUpdateAt(new Date());
        TimeTypeEnum typeEnum = TimeTypeEnum.getEnum(conf.getTimeType());
        if (typeEnum == null) {
            conf.setTimeType(TimeTypeEnum.NONE.getType());
            typeEnum = TimeTypeEnum.NONE;
        }
        switch (typeEnum) {
            case NONE:
                conf.setStart(null);
                conf.setEnd(null);
                break;
            case AFTER_START:
                if (conf.getStart() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start null");
                }
                conf.setEnd(null);
                break;
            case BEFORE_END:
                if (conf.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "end null");
                }
                conf.setStart(null);
                break;
            case BETWEEN:
                if (conf.getStart() == null || conf.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start|end null");
                }
                break;
        }
        if (NodeTypeEnum.isLeaf(conf.getType())) {
            conf.setSonIds(null);
        }
        if (NodeTypeEnum.isRelation(conf.getType())) {
            conf.setConfName(null);
            conf.setConfField(null);
        }
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
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|clazz|type");
        }
        Pair<Integer, String> res = rmiClientManager.confClazzCheck(app, clazz, type);
        if (res.getKey() == 1) {
            iceServerService.addLeafClass(app, type, clazz);
            return null;
        }
        iceServerService.removeLeafClass(app, type, clazz);
        throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app, res.getValue());
    }

    @Override
    public IceShowConf confDetail(int app, long confId, String address, long iceId) {
        if (address == null || address.equals("server")) {
            //server
            IceShowNode root = iceServerService.getConfMixById(app, confId, iceId);
            if (root == null) {
                throw new ErrorCodeException(ErrorCode.CONF_NOT_FOUND, app, "confId", confId);
            }
            IceShowConf showConf = new IceShowConf();
            showConf.setApp(app);
            showConf.setRoot(root);
            return showConf;
        }
        IceShowConf clientConf = rmiClientManager.getClientShowConf(app, confId, address);
        IceShowNode node = clientConf.getRoot();
        if (node == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, address);
        }
        assemble(app, node);
        return clientConf;
    }

    private void assemble(Integer app, IceShowNode clientNode) {
        if (clientNode == null) {
            return;
        }
        Long nodeId = clientNode.getShowConf().getNodeId();
        IceShowNode forward = clientNode.getForward();
        if (forward != null) {
            forward.setNextId(nodeId);
        }
        assembleInfoInServer(app, clientNode);
        assemble(app, forward);
        List<IceShowNode> children = clientNode.getChildren();
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            IceShowNode child = children.get(i);
            child.setIndex(i);
            child.setParentId(nodeId);
            assemble(app, child);
        }
    }

    private void assembleInfoInServer(Integer app, IceShowNode clientNode) {
        Long nodeId = clientNode.getShowConf().getNodeId();
        IceConf iceConf = iceServerService.getActiveConfById(app, nodeId);
        if (iceConf != null) {
            if (NodeTypeEnum.isRelation(iceConf.getType())) {
                clientNode.getShowConf().setLabelName(nodeId + "-" + NodeTypeEnum.getEnum(iceConf.getType()).name() + (StringUtils.hasLength(iceConf.getConfName()) ? ("-" + iceConf.getName()) : ""));
            } else {
                clientNode.getShowConf().setLabelName(nodeId + "-" + (StringUtils.hasLength(iceConf.getConfName()) ? iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1) : " ") + (StringUtils.hasLength(iceConf.getName()) ? ("-" + iceConf.getName()) : ""));
            }
            if (StringUtils.hasLength(iceConf.getName())) {
                clientNode.getShowConf().setNodeName(iceConf.getName());
            }
            if (StringUtils.hasLength(iceConf.getConfField())) {
                clientNode.getShowConf().setConfField(iceConf.getConfField());
            }
            if (StringUtils.hasLength(iceConf.getConfName())) {
                clientNode.getShowConf().setConfName(iceConf.getConfName());
            }
            clientNode.getShowConf().setNodeType(iceConf.getType());
        }
    }
}

package com.ice.server.service.impl;

import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.common.model.Pair;
import com.ice.common.utils.JacksonUtils;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.mapper.IceConfUpdateMapper;
import com.ice.server.dao.model.IceConf;
import com.ice.server.enums.EditTypeEnum;
import com.ice.server.enums.StatusEnum;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceEditNode;
import com.ice.server.model.IceLeafClass;
import com.ice.server.nio.IceNioClientManager;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class IceConfServiceImpl implements IceConfService {

    @Resource
    private IceConfMapper confMapper;

    @Resource
    private IceServerService iceServerService;

    @Resource
    private IceConfUpdateMapper confUpdateMapper;

    @Resource
    private IceNioClientManager iceNioClientManager;

    @Override
    public Long confEdit(IceEditNode editNode) {
        paramHandle(editNode);
        EditTypeEnum editTypeEnum = EditTypeEnum.getEnum(editNode.getEditType());
        switch (editTypeEnum) {
            case ADD_SON:
                return addSon(editNode);
            case EDIT:
                return edit(editNode);
            case DELETE:
                return delete(editNode);
            case ADD_FORWARD:
                return addForward(editNode);
            case EXCHANGE:
                return exchange(editNode);
            case MOVE:
                return move(editNode);
        }
        return null;
    }

    private Long move(IceEditNode editNode) {
        if (editNode.getParentId() == null || editNode.getIndex() == null || editNode.getMoveTo() == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parentId|index");
        }
        if (editNode.getMoveTo() < 0) {
            throw new ErrorCodeException(ErrorCode.CUSTOM, "can not move");
        }
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        IceConf conf = iceServerService.getMixConfById(app, editNode.getParentId(), iceId);
        if (conf == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", editNode.getParentId());
        }
        if (!StringUtils.hasLength(conf.getSonIds())) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent no child");
        }
        String[] sonIds = conf.getSonIds().split(",");
        if (sonIds.length <= 1 || editNode.getMoveTo() >= sonIds.length) {
            throw new ErrorCodeException(ErrorCode.CUSTOM, "can not move");
        }
        int index = editNode.getIndex();
        if (index < 0 || index >= sonIds.length || !sonIds[index].equals(editNode.getSelectId() + "")) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
        }
        String tmp = sonIds[index];
        sonIds[index] = sonIds[editNode.getMoveTo()];
        sonIds[editNode.getMoveTo()] = tmp;
        StringBuilder sb = new StringBuilder();
        for (String sonIdStr : sonIds) {
            sb.append(sonIdStr).append(",");
        }
        conf.setSonIds(sb.substring(0, sb.length() - 1));
        update(conf, iceId);
        return conf.getMixId();
    }

    private Long exchange(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        if (StringUtils.hasLength(editNode.getMultiplexIds())) {
            /*exchange to another id*/
            if (editNode.getParentId() == null && editNode.getNextId() == null) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "root not support exchange by id");
            }
            if (editNode.getParentId() != null) {
                //exchange son
                IceConf conf = iceServerService.getMixConfById(app, editNode.getParentId(), iceId);
                if (conf == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", editNode.getParentId());
                }
                String[] sonIdStrs = editNode.getMultiplexIds().split(",");
                Set<Long> sonIdSet = new HashSet<>(sonIdStrs.length);
                List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
                for (String sonIdStr : sonIdStrs) {
                    Long sonId = Long.valueOf(sonIdStr);
                    sonIdSet.add(sonId);
                    sonIdList.add(sonId);
                }
                List<IceConf> children = iceServerService.getMixConfListByIds(app, sonIdSet, iceId);
                if (CollectionUtils.isEmpty(children) || children.size() != sonIdSet.size()) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "sonIds", editNode.getMultiplexIds());
                }
                if (iceServerService.haveCircle(editNode.getParentId(), sonIdSet)) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check input sonIds");
                }
                String[] sonIds = conf.getSonIds().split(",");
                StringBuilder sb = new StringBuilder();
                Integer index = editNode.getIndex();
                if (index == null || index < 0 || index >= sonIds.length || !sonIds[index].equals(editNode.getSelectId() + "")) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
                }
                sonIds[index] = editNode.getMultiplexIds();
                for (String sonIdStr : sonIds) {
                    sb.append(sonIdStr).append(",");
                }
                conf.setSonIds(sb.substring(0, sb.length() - 1));
                update(conf, iceId);
                iceServerService.exchangeLink(editNode.getParentId(), editNode.getSelectId(), sonIdList);
                return conf.getMixId();
            }
            if (editNode.getNextId() != null) {
                /*exchange forward*/
                IceConf conf = iceServerService.getMixConfById(app, editNode.getNextId(), iceId);
                if (conf == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", editNode.getNextId());
                }
                Long forwardId = conf.getMixId();
                if (forwardId == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "nextId:" + editNode.getNextId() + " no forward");
                }
                Long exchangeForwardId = Long.parseLong(editNode.getMultiplexIds());
                if (iceServerService.haveCircle(editNode.getNextId(), exchangeForwardId)) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check exchangeForwardId");
                }
                conf.setForwardId(exchangeForwardId);
                update(conf, iceId);
                iceServerService.exchangeLink(editNode.getNextId(), editNode.getSelectId(), exchangeForwardId);
                return conf.getMixId();
            }
        }
        /*replace all parameters normally*/
        IceConf operateConf = iceServerService.getMixConfById(app, editNode.getSelectId(), iceId);
        if (operateConf == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "selectId", editNode.getSelectId());
        }
        operateConf.setDebug(editNode.getDebug() ? (byte) 1 : (byte) 0);
        operateConf.setInverse(editNode.getInverse() ? (byte) 1 : (byte) 0);
        operateConf.setTimeType(editNode.getTimeType());
        operateConf.setStart(editNode.getStart() == null ? null : new Date(editNode.getStart()));
        operateConf.setEnd(editNode.getEnd() == null ? null : new Date(editNode.getEnd()));
        operateConf.setType(editNode.getNodeType());
        if (!NodeTypeEnum.isRelation(editNode.getNodeType())) {
            operateConf.setSonIds(null);
        }
        operateConf.setApp(app);
        operateConf.setName(!StringUtils.hasLength(editNode.getName()) ? "" : editNode.getName());
        if (!NodeTypeEnum.isRelation(editNode.getNodeType())) {
            if (StringUtils.hasLength(editNode.getConfField())) {
                if (!JacksonUtils.isJsonObject(editNode.getConfField())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "confFiled json illegal");
                }
            }
            leafClassCheck(app, editNode.getConfName(), editNode.getNodeType());
            operateConf.setConfName(editNode.getConfName());
            operateConf.setConfField(editNode.getConfField());
        }
        update(operateConf, iceId);
        return operateConf.getMixId();
    }

    private Long addForward(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        IceConf operateConf = iceServerService.getMixConfById(app, editNode.getSelectId(), iceId);
        if (operateConf == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "selectId", editNode.getSelectId());
        }
        if (operateConf.getForwardId() != null) {
            throw new ErrorCodeException(ErrorCode.ALREADY_EXIST, "forward");
        }
        if (StringUtils.hasLength(editNode.getMultiplexIds())) {
            /*add from exist node id*/
            Long forwardId = Long.valueOf(editNode.getMultiplexIds());
            if (iceServerService.haveCircle(operateConf.getMixId(), forwardId)) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check forwardIds");
            }
            IceConf forward = iceServerService.getMixConfById(app, forwardId, iceId);
            if (forward == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "forwardId", forwardId);
            }
            operateConf.setForwardId(forwardId);
            update(operateConf, iceId);
            iceServerService.link(operateConf.getMixId(), forwardId);
            return operateConf.getMixId();
        }
        IceConf createConf = new IceConf();
        createConf.setDebug(editNode.getDebug() ? (byte) 1 : (byte) 0);
        createConf.setInverse(editNode.getInverse() ? (byte) 1 : (byte) 0);
        createConf.setTimeType(editNode.getTimeType());
        createConf.setStart(editNode.getStart() == null ? null : new Date(editNode.getStart()));
        createConf.setEnd(editNode.getEnd() == null ? null : new Date(editNode.getEnd()));
        createConf.setType(editNode.getNodeType());
        createConf.setApp(app);
        createConf.setName(!StringUtils.hasLength(editNode.getName()) ? "" : editNode.getName());
        if (!NodeTypeEnum.isRelation(editNode.getNodeType())) {
            if (StringUtils.hasLength(editNode.getConfField())) {
                if (!JacksonUtils.isJsonObject(editNode.getConfField())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "confFiled json illegal");
                }
            }
            leafClassCheck(app, editNode.getConfName(), editNode.getNodeType());
            createConf.setConfName(editNode.getConfName());
            createConf.setConfField(editNode.getConfField());
        }
        createConf.setUpdateAt(new Date());
        createConf.setStatus(StatusEnum.OFFLINE.getStatus());
        confMapper.insertSelective(createConf);
        operateConf.setForwardId(createConf.getMixId());
        createConf.setIceId(iceId);
        createConf.setConfId(createConf.getId());
        createConf.setStatus(StatusEnum.ONLINE.getStatus());
        confUpdateMapper.insertSelective(createConf);
        iceServerService.updateLocalConfUpdateCache(createConf);
        update(operateConf, iceId);
        iceServerService.link(operateConf.getMixId(), createConf.getMixId());
        return createConf.getMixId();
    }

    private Long delete(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        if (editNode.getParentId() != null) {
            //delete son
            IceConf operateConf = iceServerService.getMixConfById(app, editNode.getParentId(), iceId);
            if (operateConf == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", editNode.getParentId());
            }
            String sonIdStr = operateConf.getSonIds();
            if (!StringUtils.hasLength(sonIdStr)) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent no children", editNode.getParentId());
            }
            String[] sonIdStrs = operateConf.getSonIds().split(",");
            Integer index = editNode.getIndex();
            if (index == null || index < 0 || index >= sonIdStrs.length || !sonIdStrs[index].equals(editNode.getSelectId() + "")) {
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
                operateConf.setSonIds(str.substring(0, str.length() - 1));
            } else {
                operateConf.setSonIds(null);
            }
            update(operateConf, iceId);
            iceServerService.unlink(editNode.getParentId(), editNode.getSelectId());
            return operateConf.getMixId();
        }
        if (editNode.getNextId() != null) {
            //delete forward
            IceConf operateConf = iceServerService.getMixConfById(app, editNode.getNextId(), iceId);
            if (operateConf == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", editNode.getNextId());
            }
            if (operateConf.getForwardId() == null || !operateConf.getForwardId().equals(editNode.getSelectId())) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "nextId:" + editNode.getNextId() + " not have this forward:" + editNode.getSelectId());
            }
            operateConf.setForwardId(null);
            update(operateConf, iceId);
            iceServerService.unlink(editNode.getNextId(), editNode.getSelectId());
            return operateConf.getMixId();
        }
        throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "root not support delete");
    }

    private Long edit(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        IceConf operateConf = iceServerService.getMixConfById(app, editNode.getSelectId(), iceId);
        if (operateConf == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "selectId", editNode.getSelectId());
        }
        operateConf.setDebug(editNode.getDebug() ? (byte) 1 : (byte) 0);
        operateConf.setTimeType(editNode.getTimeType());
        operateConf.setStart(editNode.getStart() == null ? null : new Date(editNode.getStart()));
        operateConf.setEnd(editNode.getEnd() == null ? null : new Date(editNode.getEnd()));
        operateConf.setInverse(editNode.getInverse() ? (byte) 1 : (byte) 0);
        operateConf.setName(!StringUtils.hasLength(editNode.getName()) ? "" : editNode.getName());
        if (!NodeTypeEnum.isRelation(editNode.getNodeType())) {
            if (StringUtils.hasLength(editNode.getConfField())) {
                if (!JacksonUtils.isJsonObject(editNode.getConfField())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "confFiled json illegal");
                }
            }
            operateConf.setConfField(editNode.getConfField());
        }
        update(operateConf, iceId);
        return operateConf.getMixId();
    }

    private Long addSon(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        IceConf operateConf = iceServerService.getMixConfById(app, editNode.getSelectId(), iceId);
        if (operateConf == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "selectId", editNode.getSelectId());
        }
        if (!NodeTypeEnum.isRelation(operateConf.getType())) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "only relation can have son id:" + editNode.getSelectId());
        }
        if (StringUtils.hasLength(editNode.getMultiplexIds())) {
            /*add from known node id*/
            String[] sonIdStrs = editNode.getMultiplexIds().split(",");
            List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
            Set<Long> sonIdSet = new HashSet<>(sonIdStrs.length);
            for (String sonIdStr : sonIdStrs) {
                Long sonId = Long.valueOf(sonIdStr);
                sonIdList.add(sonId);
                sonIdSet.add(sonId);
            }
            if (iceServerService.haveCircle(operateConf.getMixId(), sonIdList)) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check sonIds");
            }
            List<IceConf> children = iceServerService.getMixConfListByIds(app, sonIdSet, iceId);
            if (CollectionUtils.isEmpty(children) || children.size() != sonIdSet.size()) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "one of son id not exist:" + editNode.getMultiplexIds());
            }
            operateConf.setSonIds(!StringUtils.hasLength(operateConf.getSonIds()) ?
                    String.valueOf(editNode.getMultiplexIds()) :
                    operateConf.getSonIds() + "," + editNode.getMultiplexIds());
            update(operateConf, iceId);
            iceServerService.link(operateConf.getMixId(), sonIdList);
            return operateConf.getMixId();
        }
        IceConf createConf = new IceConf();
        createConf.setDebug(editNode.getDebug() ? (byte) 1 : (byte) 0);
        createConf.setInverse(editNode.getInverse() ? (byte) 1 : (byte) 0);
        createConf.setTimeType(editNode.getTimeType());
        createConf.setStart(editNode.getStart() == null ? null : new Date(editNode.getStart()));
        createConf.setEnd(editNode.getEnd() == null ? null : new Date(editNode.getEnd()));
        createConf.setApp(app);
        createConf.setType(editNode.getNodeType());
        createConf.setName(!StringUtils.hasLength(editNode.getName()) ? "" : editNode.getName());
        if (!NodeTypeEnum.isRelation(editNode.getNodeType())) {
            if (StringUtils.hasLength(editNode.getConfField())) {
                if (!JacksonUtils.isJsonObject(editNode.getConfField())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "confFiled json illegal");
                }
            }
            leafClassCheck(app, editNode.getConfName(), editNode.getNodeType());
            createConf.setConfName(editNode.getConfName());
            createConf.setConfField(editNode.getConfField());
        }
        createConf.setUpdateAt(new Date());
        createConf.setStatus(StatusEnum.OFFLINE.getStatus());
        confMapper.insertSelective(createConf);
        operateConf.setSonIds(!StringUtils.hasLength(operateConf.getSonIds()) ?
                String.valueOf(createConf.getMixId()) :
                operateConf.getSonIds() + "," + createConf.getMixId());
        createConf.setIceId(iceId);
        createConf.setConfId(createConf.getId());
        createConf.setStatus(StatusEnum.ONLINE.getStatus());
        confUpdateMapper.insertSelective(createConf);
        iceServerService.updateLocalConfUpdateCache(createConf);
        update(operateConf, iceId);
        iceServerService.link(operateConf.getMixId(), createConf.getMixId());
        return createConf.getMixId();
    }

    private void update(IceConf operateConf, long iceId) {
        operateConf.setUpdateAt(new Date());
        if (!operateConf.isUpdate()) {
            operateConf.setIceId(iceId);
            operateConf.setConfId(operateConf.getId());
            confUpdateMapper.insertSelective(operateConf);
        } else {
            confUpdateMapper.updateByPrimaryKey(operateConf);
        }
        iceServerService.updateLocalConfUpdateCache(operateConf);
    }

    private void paramHandle(IceEditNode editNode) {
        if (editNode.getApp() == null || editNode.getIceId() == null || editNode.getSelectId() == null || editNode.getEditType() == null || EditTypeEnum.getEnum(editNode.getEditType()) == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|iceId|selectId|editType");
        }
        TimeTypeEnum typeEnum = TimeTypeEnum.getEnum(editNode.getTimeType());
        if (typeEnum == null) {
            editNode.setTimeType(TimeTypeEnum.NONE.getType());
            typeEnum = TimeTypeEnum.NONE;
        }
        switch (typeEnum) {
            case NONE:
                editNode.setStart(null);
                editNode.setEnd(null);
                break;
            case AFTER_START:
                if (editNode.getStart() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start null");
                }
                editNode.setEnd(null);
                break;
            case BEFORE_END:
                if (editNode.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "end null");
                }
                editNode.setStart(null);
                break;
            case BETWEEN:
                if (editNode.getStart() == null || editNode.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start|end null");
                }
                break;
        }
        if (editNode.getNodeType() != null && NodeTypeEnum.isRelation(editNode.getNodeType())) {
            editNode.setConfName(null);
            editNode.setConfField(null);
        }
        editNode.setDebug(editNode.getDebug() == null || editNode.getDebug());
        editNode.setInverse(editNode.getInverse() != null && editNode.getInverse());
        editNode.setTimeType(editNode.getTimeType() == null ? TimeTypeEnum.NONE.getType() : editNode.getTimeType());
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
        Pair<Integer, String> res = iceNioClientManager.confClazzCheck(app, clazz, type);
        if (res != null && res.getKey() == 1) {
            iceServerService.addLeafClass(app, type, clazz);
            return null;
        }
        iceServerService.removeLeafClass(app, type, clazz);
        throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app, res == null ? null : res.getValue());
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
        IceShowConf clientConf = iceNioClientManager.getClientShowConf(app, confId, address);
        if (clientConf == null || clientConf.getRoot() == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, address);
        }
        assemble(app, clientConf.getRoot());
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
                clientNode.getShowConf().setLabelName(nodeId + "-" + NodeTypeEnum.getEnum(iceConf.getType()).name() + (StringUtils.hasLength(iceConf.getName()) ? ("-" + iceConf.getName()) : ""));
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

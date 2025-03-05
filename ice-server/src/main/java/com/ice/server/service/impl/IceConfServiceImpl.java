package com.ice.server.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.ice.common.constant.Constant;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.common.model.LeafNodeInfo;
import com.ice.common.utils.UUIDUtils;
import com.ice.core.client.IceNioModel;
import com.ice.core.client.NioOps;
import com.ice.core.client.NioType;
import com.ice.core.utils.JacksonUtils;
import com.ice.server.constant.ServerConstant;
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
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * @author waitmoon
 */
@Slf4j
@Service
public class IceConfServiceImpl implements IceConfService {

    @Autowired
    private IceConfMapper confMapper;

    @Autowired
    private IceServerService iceServerService;

    @Autowired
    private IceConfUpdateMapper confUpdateMapper;

    @Autowired
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

    private synchronized Long move(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        if (editNode.getParentId() != null && editNode.getIndex() != null) {
            //from parent`s child
            IceConf parent = iceServerService.getMixConfById(app, editNode.getParentId(), iceId);
            if (parent == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", editNode.getParentId());
            }
            if (!StringUtils.hasLength(parent.getSonIds())) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent no child");
            }
            if (NodeTypeEnum.isLeaf(parent.getType())) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parentId not parent");
            }
            String[] sonIds = parent.getSonIds().split(Constant.REGEX_COMMA);
            int index = editNode.getIndex();
            if (index < 0 || index >= sonIds.length || !sonIds[index].equals(editNode.getSelectId() + "")) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
            }
            if (editNode.getMoveToNextId() != null) {
                //from some parent`s child to forward
                IceConf moveToNext = iceServerService.getMixConfById(app, editNode.getMoveToNextId(), iceId);
                if (moveToNext.getForwardId() != null) {
                    throw new ErrorCodeException(ErrorCode.CUSTOM, "move to moveToNext:" + editNode.getMoveToNextId() + " already has forward");
                }
                if (iceServerService.haveCircle(moveToNext.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }
                moveToNext.setForwardId(editNode.getSelectId());
                StringBuilder sb = new StringBuilder();
                sonIds[index] = null;
                for (String sonIdStr : sonIds) {
                    if (sonIdStr != null) {
                        sb.append(sonIdStr).append(Constant.REGEX_COMMA);
                    }
                }
                parent.setSonIds(Constant.removeLast(sb));
                update(moveToNext, iceId);
                update(parent, iceId);
                iceServerService.unlink(parent.getMixId(), editNode.getSelectId());
                iceServerService.link(moveToNext.getMixId(), editNode.getSelectId());
                return editNode.getSelectId();
            }
            //move between child nodes
            if (editNode.getMoveToParentId() == null || editNode.getMoveToParentId().equals(editNode.getParentId())) {
                if (sonIds.length == 1 || (editNode.getMoveTo() == null && index == sonIds.length - 1) || editNode.getIndex().equals(editNode.getMoveTo())) {
                    //ignore
                    return editNode.getSelectId();
                }
                //same parent
                StringBuilder sb = new StringBuilder();
                if (editNode.getMoveTo() == null || editNode.getMoveTo() >= sonIds.length) {
                    //default move to the end
                    sonIds[index] = null;
                    for (String sonIdStr : sonIds) {
                        if (sonIdStr != null) {
                            sb.append(sonIdStr).append(Constant.REGEX_COMMA);
                        }
                    }
                    parent.setSonIds(sb.toString() + editNode.getSelectId());
                } else {
                    for (int i = 0; i < sonIds.length; i++) {
                        if (editNode.getMoveTo().equals(i)) {
                            sb.append(editNode.getSelectId()).append(Constant.REGEX_COMMA);
                        }
                        if (index != i) {
                            sb.append(sonIds[i]).append(Constant.REGEX_COMMA);
                        }
                    }
                    parent.setSonIds(Constant.removeLast(sb));
                }
                update(parent, iceId);
            } else {
                //different parent
                IceConf moveToParent = iceServerService.getMixConfById(app, editNode.getMoveToParentId(), iceId);
                if (moveToParent == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "moveToParentId", editNode.getMoveToParentId());
                }
                if (NodeTypeEnum.isLeaf(moveToParent.getType())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "move to parentId not parent");
                }
                if (iceServerService.haveCircle(moveToParent.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }
                if (editNode.getMoveTo() == null) {
                    //default move to the end
                    moveToParent.setSonIds(StringUtils.hasLength(moveToParent.getSonIds()) ? (moveToParent.getSonIds() + Constant.REGEX_COMMA + editNode.getSelectId()) : (editNode.getSelectId() + ""));
                    StringBuilder sb = new StringBuilder();
                    sonIds[index] = null;
                    for (String sonIdStr : sonIds) {
                        if (sonIdStr != null) {
                            sb.append(sonIdStr).append(Constant.REGEX_COMMA);
                        }
                    }
                    parent.setSonIds(Constant.removeLast(sb));
                } else {
                    if (!StringUtils.hasLength(moveToParent.getSonIds())) {
                        moveToParent.setSonIds(editNode.getSelectId() + "");
                    } else {
                        String[] moveToSonIds = moveToParent.getSonIds().split(Constant.REGEX_COMMA);
                        if (editNode.getMoveTo() >= moveToSonIds.length || editNode.getMoveTo() < 0) {
                            //put on last
                            moveToParent.setSonIds(moveToParent.getSonIds() + Constant.REGEX_COMMA + editNode.getSelectId());
                        } else {
                            StringBuilder moveToSb = new StringBuilder();
                            for (int i = 0; i < moveToSonIds.length; i++) {
                                if (editNode.getMoveTo().equals(i)) {
                                    moveToSb.append(editNode.getSelectId()).append(Constant.REGEX_COMMA);
                                }
                                moveToSb.append(moveToSonIds[i]).append(Constant.REGEX_COMMA);
                            }
                            moveToParent.setSonIds(Constant.removeLast(moveToSb));
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    sonIds[index] = null;
                    for (String sonIdStr : sonIds) {
                        if (sonIdStr != null) {
                            sb.append(sonIdStr).append(Constant.REGEX_COMMA);
                        }
                    }
                    parent.setSonIds(Constant.removeLast(sb));
                }
                update(moveToParent, iceId);
                update(parent, iceId);
                iceServerService.unlink(parent.getMixId(), editNode.getSelectId());
                iceServerService.link(moveToParent.getMixId(), editNode.getSelectId());
            }
            return editNode.getSelectId();
        }

        if (editNode.getNextId() != null) {
            //from next`s forward
            IceConf next = iceServerService.getMixConfById(app, editNode.getNextId(), iceId);
            if (next == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", editNode.getParentId());
            }
            if (next.getForwardId() == null || !next.getForwardId().equals(editNode.getSelectId())) {
                throw new ErrorCodeException(ErrorCode.CUSTOM, "next:" + editNode.getNextId() + " not have this forward:" + editNode.getSelectId());
            }
            if (editNode.getMoveToNextId() != null) {
                //move between forwards
                if (editNode.getNextId().equals(editNode.getMoveToNextId())) {
                    return editNode.getSelectId();
                }
                IceConf moveToNext = iceServerService.getMixConfById(app, editNode.getMoveToNextId(), iceId);
                if (moveToNext.getForwardId() != null) {
                    throw new ErrorCodeException(ErrorCode.CUSTOM, "move to next:" + editNode.getMoveToNextId() + " already has forward");
                }
                if (iceServerService.haveCircle(moveToNext.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }
                moveToNext.setForwardId(editNode.getSelectId());
                next.setForwardId(null);
                update(moveToNext, iceId);
                update(next, iceId);
                iceServerService.unlink(next.getMixId(), editNode.getSelectId());
                iceServerService.link(moveToNext.getMixId(), editNode.getSelectId());
                return editNode.getSelectId();
            }
            //forward move to parent
            if (editNode.getMoveToParentId() != null) {
                IceConf moveToParent = iceServerService.getMixConfById(app, editNode.getMoveToParentId(), iceId);
                if (moveToParent == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "moveToParentId", editNode.getMoveToParentId());
                }
                if (NodeTypeEnum.isLeaf(moveToParent.getType())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "move to parentId not parent");
                }
                if (iceServerService.haveCircle(moveToParent.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }
                if (editNode.getMoveTo() == null) {
                    //default move to the end
                    moveToParent.setSonIds(StringUtils.hasLength(moveToParent.getSonIds()) ? (moveToParent.getSonIds() + Constant.REGEX_COMMA + editNode.getSelectId()) : (editNode.getSelectId() + ""));
                    next.setForwardId(null);
                } else {
                    if (!StringUtils.hasLength(moveToParent.getSonIds())) {
                        moveToParent.setSonIds(editNode.getSelectId() + "");
                    } else {
                        String[] moveToSonIds = moveToParent.getSonIds().split(Constant.REGEX_COMMA);
                        if (editNode.getMoveTo() >= moveToSonIds.length || editNode.getMoveTo() < 0) {
                            //put on last
                            moveToParent.setSonIds(moveToParent.getSonIds() + Constant.REGEX_COMMA + editNode.getSelectId());
                        } else {
                            StringBuilder moveToSb = new StringBuilder();
                            for (int i = 0; i < moveToSonIds.length; i++) {
                                if (editNode.getMoveTo().equals(i)) {
                                    moveToSb.append(editNode.getSelectId()).append(Constant.REGEX_COMMA);
                                }
                                moveToSb.append(moveToSonIds[i]).append(Constant.REGEX_COMMA);
                            }
                            moveToParent.setSonIds(Constant.removeLast(moveToSb));
                        }
                    }
                    next.setForwardId(null);
                }
                update(moveToParent, iceId);
                update(next, iceId);
                iceServerService.unlink(next.getMixId(), editNode.getSelectId());
                iceServerService.link(moveToParent.getMixId(), editNode.getSelectId());
                return editNode.getSelectId();
            }
        }
        return editNode.getSelectId();
    }

    private Long exchange(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();
        if (StringUtils.hasText(editNode.getMultiplexIds())) {
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
                String[] sonIdStrs = editNode.getMultiplexIds().split(Constant.REGEX_COMMA);
                Set<Long> sonIdSet = new HashSet<>(sonIdStrs.length);
                List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
                for (String sonIdStr : sonIdStrs) {
                    Long sonId = Long.valueOf(sonIdStr);
                    sonIdSet.add(sonId);
                    sonIdList.add(sonId);
                }
                List<IceConf> children = iceServerService.getMixConfListByIds(app, sonIdSet, iceId);
                if (CollectionUtils.isEmpty(children) || children.size() != sonIdSet.size()) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "one of sonId", editNode.getMultiplexIds());
                }
                if (iceServerService.haveCircle(editNode.getParentId(), sonIdSet)) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check input sonIds");
                }
                String[] sonIds = conf.getSonIds().split(Constant.REGEX_COMMA);
                StringBuilder sb = new StringBuilder();
                Integer index = editNode.getIndex();
                if (index == null || index < 0 || index >= sonIds.length || !sonIds[index].equals(editNode.getSelectId() + "")) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
                }
                sonIds[index] = editNode.getMultiplexIds();
                for (String sonIdStr : sonIds) {
                    sb.append(sonIdStr).append(Constant.REGEX_COMMA);
                }
                conf.setSonIds(Constant.removeLast(sb));
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
        if (NodeTypeEnum.isRelation(operateConf.getType()) && !NodeTypeEnum.isRelation(editNode.getNodeType())) {
            //origin is relation now not
            operateConf.setSonIds(null);
            if (StringUtils.hasLength(operateConf.getSonIds())) {
                //unlink all child
                String[] sonIdStrs = operateConf.getSonIds().split(Constant.REGEX_COMMA);
                for (String sonIdStr : sonIdStrs) {
                    iceServerService.unlink(operateConf.getMixId(), Long.valueOf(sonIdStr));
                }
            }
        }
        operateConf.setType(editNode.getNodeType());
        operateConf.setApp(app);
        //use old name
        operateConf.setName(!StringUtils.hasLength(editNode.getName()) ? operateConf.getName() : editNode.getName());
        if (NodeTypeEnum.isLeaf(editNode.getNodeType())) {
            LeafNodeInfo leafNodeInfo = leafClassCheck(app, editNode.getConfName(), editNode.getNodeType());
            if (StringUtils.hasLength(editNode.getConfField())) {
                String checkRes = ServerConstant.checkIllegalAndAdjustJson(editNode, leafNodeInfo);
                if (checkRes != null) {
                    throw new ErrorCodeException(ErrorCode.CONFIG_FILED_ILLEGAL, checkRes);
                }
            }
            operateConf.setConfName(editNode.getConfName());
            operateConf.setConfField(editNode.getConfField());
            iceServerService.increaseLeafClass(app, editNode.getNodeType(), editNode.getConfName());
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
        if (NodeTypeEnum.isLeaf(editNode.getNodeType())) {
            LeafNodeInfo leafNodeInfo = leafClassCheck(app, editNode.getConfName(), editNode.getNodeType());
            if (StringUtils.hasLength(editNode.getConfField())) {
                String checkRes = ServerConstant.checkIllegalAndAdjustJson(editNode, leafNodeInfo);
                if (checkRes != null) {
                    throw new ErrorCodeException(ErrorCode.CONFIG_FILED_ILLEGAL, checkRes);
                }
            }
            createConf.setConfName(editNode.getConfName());
            createConf.setConfField(editNode.getConfField());
            iceServerService.increaseLeafClass(app, editNode.getNodeType(), editNode.getConfName());
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
            String[] sonIdStrs = operateConf.getSonIds().split(Constant.REGEX_COMMA);
            Integer index = editNode.getIndex();
            if (index == null || index < 0 || index >= sonIdStrs.length || !sonIdStrs[index].equals(editNode.getSelectId() + "")) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "parent do not have this son with input index");
            }
            sonIdStrs[index] = null;
            StringBuilder sb = new StringBuilder();
            for (String idStr : sonIdStrs) {
                if (idStr != null) {
                    sb.append(idStr).append(Constant.REGEX_COMMA);
                }
            }
            operateConf.setSonIds(Constant.removeLast(sb));
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
        if (NodeTypeEnum.isLeaf(operateConf.getType())) {
            LeafNodeInfo leafNodeInfo = leafClassCheck(app, operateConf.getConfName(), operateConf.getType());
            if (StringUtils.hasLength(editNode.getConfField())) {
                String checkRes = ServerConstant.checkIllegalAndAdjustJson(editNode, leafNodeInfo);
                if (checkRes != null) {
                    throw new ErrorCodeException(ErrorCode.CONFIG_FILED_ILLEGAL, checkRes);
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
        if (NodeTypeEnum.isLeaf(operateConf.getType())) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "only relation can have son id:" + editNode.getSelectId());
        }
        if (StringUtils.hasLength(editNode.getMultiplexIds())) {
            /*add from known node id*/
            String[] sonIdStrs = editNode.getMultiplexIds().split(Constant.REGEX_COMMA);
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
                    operateConf.getSonIds() + Constant.REGEX_COMMA + editNode.getMultiplexIds());
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
        if (NodeTypeEnum.isLeaf(editNode.getNodeType())) {
            LeafNodeInfo leafNodeInfo = leafClassCheck(app, editNode.getConfName(), editNode.getNodeType());
            if (StringUtils.hasLength(editNode.getConfField())) {
                String checkRes = ServerConstant.checkIllegalAndAdjustJson(editNode, leafNodeInfo);
                if (checkRes != null) {
                    throw new ErrorCodeException(ErrorCode.CONFIG_FILED_ILLEGAL, checkRes);
                }
            }
            createConf.setConfName(editNode.getConfName());
            createConf.setConfField(editNode.getConfField());
            iceServerService.increaseLeafClass(app, editNode.getNodeType(), editNode.getConfName());
        }
        createConf.setUpdateAt(new Date());
        createConf.setStatus(StatusEnum.OFFLINE.getStatus());
        confMapper.insertSelective(createConf);
        operateConf.setSonIds(!StringUtils.hasLength(operateConf.getSonIds()) ?
                String.valueOf(createConf.getMixId()) :
                operateConf.getSonIds() + Constant.REGEX_COMMA + createConf.getMixId());
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
        if (!operateConf.isUpdatingConf()) {
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
        if (StringUtils.hasLength(editNode.getName()) && editNode.getName().length() > 50) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "name too long (>50)");
        }
        editNode.setDebug(editNode.getDebug() == null || editNode.getDebug());
        editNode.setInverse(editNode.getInverse() != null && editNode.getInverse());
        editNode.setTimeType(editNode.getTimeType() == null ? TimeTypeEnum.NONE.getType() : editNode.getTimeType());
    }

    @Override
    public synchronized List<IceLeafClass> getConfLeafClass(int app, byte type) {
        List<IceLeafClass> result = new ArrayList<>();
        Map<String, LeafNodeInfo> clientClazzInfoMap = iceNioClientManager.getLeafTypeClasses(app, type);
        Map<String, Integer> leafClassDBMap = iceServerService.getLeafClassMap(app, type);
        if (CollectionUtils.isEmpty(clientClazzInfoMap)) {
            //no leaf class with type found in client, used db config instead
            if (leafClassDBMap != null) {
                for (Map.Entry<String, Integer> entry : leafClassDBMap.entrySet()) {
                    IceLeafClass leafClass = new IceLeafClass();
                    leafClass.setFullName(entry.getKey());
                    leafClass.setCount(entry.getValue());
                    result.add(leafClass);
                }
            }
        } else {
            if (leafClassDBMap != null) {
                for (Map.Entry<String, LeafNodeInfo> leafNodeInfoEntry : clientClazzInfoMap.entrySet()) {
                    if (!leafClassDBMap.containsKey(leafNodeInfoEntry.getKey())) {
                        //add not used leaf class
                        IceLeafClass leafClass = new IceLeafClass();
                        leafClass.setFullName(leafNodeInfoEntry.getKey());
                        leafClass.setName(leafNodeInfoEntry.getValue().getName());
                        leafClass.setCount(0);
                        result.add(leafClass);
                    }
                }
                for (Map.Entry<String, Integer> entry : leafClassDBMap.entrySet()) {
                    //used to assembly by used cnt from db config
                    LeafNodeInfo nodeInfo = clientClazzInfoMap.get(entry.getKey());
                    if (nodeInfo != null) {
                        //add only class found from client
                        IceLeafClass leafClass = new IceLeafClass();
                        leafClass.setFullName(entry.getKey());
                        leafClass.setCount(entry.getValue());
                        leafClass.setName(nodeInfo.getName());
                        result.add(leafClass);
                    }
                }
            } else {
                for (Map.Entry<String, LeafNodeInfo> leafNodeInfoEntry : clientClazzInfoMap.entrySet()) {
                    IceLeafClass leafClass = new IceLeafClass();
                    leafClass.setFullName(leafNodeInfoEntry.getKey());
                    leafClass.setName(leafNodeInfoEntry.getValue().getName());
                    leafClass.setCount(0);
                    result.add(leafClass);
                }
            }
        }
        result.sort(Comparator.comparingInt(IceLeafClass::sortNegativeCount));
        return result;
    }

    @Override
    public LeafNodeInfo leafClassCheck(int app, String clazz, byte type) {
        NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(type);
        if (!StringUtils.hasLength(clazz) || typeEnum == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|clazz|type");
        }
        return confClazzCheck(app, clazz, type);
    }

    private synchronized LeafNodeInfo confClazzCheck(int app, String clazz, byte type) {
        Map<String, LeafNodeInfo> clazzInfoMap = iceNioClientManager.getLeafTypeClasses(app, type);
        if (!CollectionUtils.isEmpty(clazzInfoMap)) {
            LeafNodeInfo leafNodeInfo = clazzInfoMap.get(clazz);
            if (leafNodeInfo != null) {
                //one of available client have this clazz
                return leafNodeInfo;
            }
        }
        //not found in client init leaf node, try search on db config
        Map<String, Integer> leafClazzDBMap = iceServerService.getLeafClassMap(app, type);
        if (!CollectionUtils.isEmpty(leafClazzDBMap) && leafClazzDBMap.containsKey(clazz)) {
            return null;
        }
        //not found in client init leaf node and db config, try search on one of real client
        Channel channel = iceNioClientManager.getClientSocketChannel(app, null);
        if (channel == null) {
            throw new ErrorCodeException(ErrorCode.NO_AVAILABLE_CLIENT, app);
        }
        IceNioModel request = new IceNioModel();
        request.setClazz(clazz);
        request.setNodeType(type);
        request.setApp(app);
        request.setId(UUIDUtils.generateUUID22());
        request.setType(NioType.REQ);
        request.setOps(NioOps.CLAZZ_CHECK);
        IceNioModel response = iceNioClientManager.getResult(channel, request);
        if (response == null || response.getClazzCheck() == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app, "unknown");
        }
        if (response.getClazzCheck().getKey() != 1) {
            throw new ErrorCodeException(ErrorCode.REMOTE_ERROR, app, response.getClazzCheck().getValue());
        }
        return null;
    }

    @Override
    public IceShowConf confDetail(int app, long confId, String address, long iceId) {
        if (address == null || address.equals("server")) {
            //server
            IceShowNode root = iceServerService.getConfMixById(app, confId, iceId);
            if (root == null) {
                throw new ErrorCodeException(ErrorCode.CONF_NOT_FOUND, app, "confId", confId);
            }
            IceShowConf serverConf = new IceShowConf();
            serverConf.setApp(app);
            serverConf.setRoot(root);
            addUniqueKey(root, null, true, false);
            return serverConf;
        }
        IceShowConf clientConf = iceNioClientManager.getClientShowConf(app, confId, address);
        if (clientConf == null || clientConf.getRoot() == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, address);
        }
        assemble(app, clientConf.getRoot(), address);
        addUniqueKey(clientConf.getRoot(), null, true, false);
        return clientConf;
    }

    /**
     * add uniqueKey for web ui
     *
     * @param node   node
     * @param prefix prefix
     */
    private void addUniqueKey(IceShowNode node, String prefix, boolean root, boolean forward) {
        if (node == null) {
            return;
        }
        String uniqueKey = (prefix == null ? "" : prefix + "_") + node.getShowConf().getNodeId() + "_" + (node.getIndex() == null ? 0 : node.getIndex());
        if (root) {
            uniqueKey = uniqueKey + "_r";
        }
        if (forward) {
            uniqueKey = uniqueKey + "_f";
        }
        node.getShowConf().setUniqueKey(uniqueKey);
        if (node.getForward() != null) {
            addUniqueKey(node.getForward(), uniqueKey, false, true);
        }
        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (IceShowNode child : node.getChildren()) {
                addUniqueKey(child, uniqueKey, false, false);
            }
        }
    }

    private void assemble(Integer app, IceShowNode clientNode, String address) {
        if (clientNode == null) {
            return;
        }
        Long nodeId = clientNode.getShowConf().getNodeId();
        IceShowNode forward = clientNode.getForward();
        if (forward != null) {
            forward.setNextId(nodeId);
        }
        assembleInfoInServer(app, clientNode, address);
        assemble(app, forward, address);
        List<IceShowNode> children = clientNode.getChildren();
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (int i = 0; i < children.size(); i++) {
            IceShowNode child = children.get(i);
            child.setIndex(i);
            child.setParentId(nodeId);
            assemble(app, child, address);
        }
    }

    private void assembleInfoInServer(Integer app, IceShowNode clientNode, String address) {
        IceShowNode.NodeShowConf nodeShowConf = clientNode.getShowConf();
        Long nodeId = nodeShowConf.getNodeId();
        if (NodeTypeEnum.isLeaf(nodeShowConf.getNodeType())) {
            //assemble filed info
            LeafNodeInfo nodeInfo = iceNioClientManager.getNodeInfo(app, address, nodeShowConf.getConfName(), nodeShowConf.getNodeType());
            if (nodeInfo != null) {
                nodeShowConf.setHaveMeta(true);
                nodeShowConf.setNodeInfo(nodeInfo);
                String confJson = nodeShowConf.getConfField();
                JsonNode jsonNode = JacksonUtils.readTree(confJson);
                if (jsonNode != null) {
                    if (!CollectionUtils.isEmpty(nodeInfo.getIceFields())) {
                        for (LeafNodeInfo.IceFieldInfo fieldInfo : nodeInfo.getIceFields()) {
                            JsonNode value = jsonNode.get(fieldInfo.getField());
                            if (value != null && value.isNull()) {
                                fieldInfo.setValueNull(true);
                            } else {
                                if (value != null) {
                                    fieldInfo.setValue(value);
                                }
                            }
                        }
                    }
                    if (!CollectionUtils.isEmpty(nodeInfo.getHideFields())) {
                        for (LeafNodeInfo.IceFieldInfo hideFiledInfo : nodeInfo.getHideFields()) {
                            JsonNode value = jsonNode.get(hideFiledInfo.getField());
                            if (value != null && value.isNull()) {
                                hideFiledInfo.setValueNull(true);
                            } else {
                                if (value != null) {
                                    hideFiledInfo.setValue(value);
                                }
                            }
                        }
                    }
                }
            } else {
                nodeShowConf.setHaveMeta(false);
            }
        } else {
            nodeShowConf.setHaveMeta(true);
        }
        //reset to default
        if (nodeShowConf.getInverse() == null) {
            nodeShowConf.setInverse(false);
        }
        if (nodeShowConf.getDebug() == null) {
            nodeShowConf.setDebug(true);
        }
        //assemble name from server
        IceConf iceConf = iceServerService.getActiveConfById(app, nodeId);
        if (iceConf != null) {
            if (StringUtils.hasLength(iceConf.getName())) {
                nodeShowConf.setNodeName(iceConf.getName());
            }
            if (NodeTypeEnum.isRelation(iceConf.getType())) {
                clientNode.getShowConf().setLabelName(nodeId + "-" + NodeTypeEnum.getEnum(iceConf.getType()).name() + (StringUtils.hasLength(iceConf.getName()) ? ("-" + iceConf.getName()) : ""));
            } else {
                clientNode.getShowConf().setLabelName(nodeId + "-" + (StringUtils.hasLength(iceConf.getConfName()) ? iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1) : " ") + (StringUtils.hasLength(iceConf.getName()) ? ("-" + iceConf.getName()) : ""));
            }
        }
    }
}

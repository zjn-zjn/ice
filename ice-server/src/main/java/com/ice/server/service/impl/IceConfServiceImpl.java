package com.ice.server.service.impl;

import com.ice.common.constant.Constant;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.core.utils.JacksonUtils;
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

/**
 * @author waitmoon
 */
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
            if (!NodeTypeEnum.isRelation(parent.getType())) {
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
                if (editNode.getMoveTo() == null) {
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
                        if (editNode.getMoveTo() < index && editNode.getMoveTo().equals(i)) {
                            sb.append(editNode.getSelectId()).append(Constant.REGEX_COMMA);
                        }
                        if (index != i) {
                            sb.append(sonIds[i]).append(Constant.REGEX_COMMA);
                        }
                        if (editNode.getMoveTo() > index && editNode.getMoveTo().equals(i)) {
                            sb.append(editNode.getSelectId()).append(Constant.REGEX_COMMA);
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
                if (!NodeTypeEnum.isRelation(moveToParent.getType())) {
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
                if (!NodeTypeEnum.isRelation(moveToParent.getType())) {
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
        editNode.setDebug(editNode.getDebug() == null || editNode.getDebug());
        editNode.setInverse(editNode.getInverse() != null && editNode.getInverse());
        editNode.setTimeType(editNode.getTimeType() == null ? TimeTypeEnum.NONE.getType() : editNode.getTimeType());
    }

    @Override
    public synchronized List<IceLeafClass> getConfLeafClass(int app, byte type) {
        List<IceLeafClass> result = new ArrayList<>();
        Set<String> clientLeafClassSet = iceNioClientManager.getLeafTypeClasses(app, type);
        if (CollectionUtils.isEmpty(clientLeafClassSet)) {
            //no leaf class with type found in client
            return result;
        }
        Map<String, Integer> leafClassDBMap = iceServerService.getLeafClassMap(app, type);
        if (leafClassDBMap != null) {
            for (String clientLeafClass : clientLeafClassSet) {
                if (!leafClassDBMap.containsKey(clientLeafClass)) {
                    //add not used leaf class
                    IceLeafClass leafClass = new IceLeafClass();
                    leafClass.setFullName(clientLeafClass);
                    leafClass.setCount(0);
                    leafClass.setShortName(clientLeafClass.substring(clientLeafClass.lastIndexOf('.') + 1));
                    result.add(leafClass);
                }
            }
            for (Map.Entry<String, Integer> entry : leafClassDBMap.entrySet()) {
                if (clientLeafClassSet.contains(entry.getKey())) {
                    //add only class found from client
                    IceLeafClass leafClass = new IceLeafClass();
                    leafClass.setFullName(entry.getKey());
                    leafClass.setCount(entry.getValue());
                    leafClass.setShortName(entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1));
                    result.add(leafClass);
                }
            }
        } else {
            for (String clientLeafClass : clientLeafClassSet) {
                IceLeafClass leafClass = new IceLeafClass();
                leafClass.setFullName(clientLeafClass);
                leafClass.setCount(1);
                leafClass.setShortName(clientLeafClass.substring(clientLeafClass.lastIndexOf('.') + 1));
                result.add(leafClass);
            }
        }
        result.sort(Comparator.comparingInt(IceLeafClass::sortNegativeCount));
        return result;
    }

    @Override
    public void leafClassCheck(int app, String clazz, byte type) {
        NodeTypeEnum typeEnum = NodeTypeEnum.getEnum(type);
        if (!StringUtils.hasLength(clazz) || typeEnum == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|clazz|type");
        }
        try {
            iceNioClientManager.confClazzCheck(app, clazz, type);
            iceServerService.addLeafClass(app, type, clazz);
        } catch (Exception e) {
            iceServerService.removeLeafClass(app, type, clazz);
            throw e;
        }
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

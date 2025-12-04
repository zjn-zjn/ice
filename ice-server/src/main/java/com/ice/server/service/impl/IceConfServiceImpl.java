package com.ice.server.service.impl;

import com.ice.common.constant.Constant;
import com.ice.common.constant.IceStorageConstants;
import com.ice.common.dto.IceConfDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.IceShowNode;
import com.ice.common.model.LeafNodeInfo;
import com.ice.server.constant.ServerConstant;
import com.ice.server.dao.model.IceConf;
import com.ice.server.enums.EditTypeEnum;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceEditNode;
import com.ice.server.model.IceLeafClass;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
import com.ice.server.storage.IceClientManager;
import com.ice.server.storage.IceFileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author waitmoon
 */
@Slf4j
@Service
public class IceConfServiceImpl implements IceConfService {

    private final IceFileStorageService storageService;
    private final IceServerService iceServerService;
    private final IceClientManager clientManager;

    public IceConfServiceImpl(IceFileStorageService storageService, IceServerService iceServerService,
                              IceClientManager clientManager) {
        this.storageService = storageService;
        this.iceServerService = iceServerService;
        this.clientManager = clientManager;
    }

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
                IceConf moveToNext = iceServerService.getMixConfById(app, editNode.getMoveToNextId(), iceId);
                if (moveToNext.getForwardId() != null) {
                    throw new ErrorCodeException(ErrorCode.CUSTOM, "move to moveToNext:" + editNode.getMoveToNextId() + " already has forward");
                }
                if (iceServerService.haveCircle(app, iceId, moveToNext.getMixId(), editNode.getSelectId())) {
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
                return editNode.getSelectId();
            }

            if (editNode.getMoveToParentId() == null || editNode.getMoveToParentId().equals(editNode.getParentId())) {
                if (sonIds.length == 1 || (editNode.getMoveTo() == null && index == sonIds.length - 1) || editNode.getIndex().equals(editNode.getMoveTo())) {
                    return editNode.getSelectId();
                }
                StringBuilder sb = new StringBuilder();
                if (editNode.getMoveTo() == null || editNode.getMoveTo() >= sonIds.length) {
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
                IceConf moveToParent = iceServerService.getMixConfById(app, editNode.getMoveToParentId(), iceId);
                if (moveToParent == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "moveToParentId", editNode.getMoveToParentId());
                }
                if (NodeTypeEnum.isLeaf(moveToParent.getType())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "move to parentId not parent");
                }
                if (iceServerService.haveCircle(app, iceId, moveToParent.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }

                if (editNode.getMoveTo() == null) {
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
            }
            return editNode.getSelectId();
        }

        if (editNode.getNextId() != null) {
            IceConf next = iceServerService.getMixConfById(app, editNode.getNextId(), iceId);
            if (next == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", editNode.getParentId());
            }
            if (next.getForwardId() == null || !next.getForwardId().equals(editNode.getSelectId())) {
                throw new ErrorCodeException(ErrorCode.CUSTOM, "next:" + editNode.getNextId() + " not have this forward:" + editNode.getSelectId());
            }
            if (editNode.getMoveToNextId() != null) {
                if (editNode.getNextId().equals(editNode.getMoveToNextId())) {
                    return editNode.getSelectId();
                }
                IceConf moveToNext = iceServerService.getMixConfById(app, editNode.getMoveToNextId(), iceId);
                if (moveToNext.getForwardId() != null) {
                    throw new ErrorCodeException(ErrorCode.CUSTOM, "move to next:" + editNode.getMoveToNextId() + " already has forward");
                }
                if (iceServerService.haveCircle(app, iceId, moveToNext.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }
                moveToNext.setForwardId(editNode.getSelectId());
                next.setForwardId(null);
                update(moveToNext, iceId);
                update(next, iceId);
                return editNode.getSelectId();
            }
            if (editNode.getMoveToParentId() != null) {
                IceConf moveToParent = iceServerService.getMixConfById(app, editNode.getMoveToParentId(), iceId);
                if (moveToParent == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "moveToParentId", editNode.getMoveToParentId());
                }
                if (NodeTypeEnum.isLeaf(moveToParent.getType())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "move to parentId not parent");
                }
                if (iceServerService.haveCircle(app, iceId, moveToParent.getMixId(), editNode.getSelectId())) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "can not move, circles found");
                }
                if (editNode.getMoveTo() == null) {
                    moveToParent.setSonIds(StringUtils.hasLength(moveToParent.getSonIds()) ? (moveToParent.getSonIds() + Constant.REGEX_COMMA + editNode.getSelectId()) : (editNode.getSelectId() + ""));
                    next.setForwardId(null);
                } else {
                    if (!StringUtils.hasLength(moveToParent.getSonIds())) {
                        moveToParent.setSonIds(editNode.getSelectId() + "");
                    } else {
                        String[] moveToSonIds = moveToParent.getSonIds().split(Constant.REGEX_COMMA);
                        if (editNode.getMoveTo() >= moveToSonIds.length || editNode.getMoveTo() < 0) {
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
                return editNode.getSelectId();
            }
        }
        return editNode.getSelectId();
    }

    private Long exchange(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();

        if (StringUtils.hasText(editNode.getMultiplexIds())) {
            if (editNode.getParentId() == null && editNode.getNextId() == null) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "root not support exchange by id");
            }
            if (editNode.getParentId() != null) {
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
                if (iceServerService.haveCircle(app, iceId, editNode.getParentId(), sonIdSet)) {
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
                return conf.getMixId();
            }
            if (editNode.getNextId() != null) {
                IceConf conf = iceServerService.getMixConfById(app, editNode.getNextId(), iceId);
                if (conf == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", editNode.getNextId());
                }
                Long forwardId = conf.getForwardId();
                if (forwardId == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "nextId:" + editNode.getNextId() + " no forward");
                }
                Long exchangeForwardId = Long.parseLong(editNode.getMultiplexIds());
                if (iceServerService.haveCircle(app, iceId, editNode.getNextId(), exchangeForwardId)) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check exchangeForwardId");
                }
                conf.setForwardId(exchangeForwardId);
                update(conf, iceId);
                return conf.getMixId();
            }
        }

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
            operateConf.setSonIds(null);
        }
        operateConf.setType(editNode.getNodeType());
        operateConf.setApp(app);
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
            Long forwardId = Long.valueOf(editNode.getMultiplexIds());
            if (iceServerService.haveCircle(app, iceId, operateConf.getMixId(), forwardId)) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "circles found please check forwardIds");
            }
            IceConf forward = iceServerService.getMixConfById(app, forwardId, iceId);
            if (forward == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "forwardId", forwardId);
            }
            operateConf.setForwardId(forwardId);
            update(operateConf, iceId);
            return operateConf.getMixId();
        }

        IceConf createConf = createNewConf(editNode, app);
        operateConf.setForwardId(createConf.getMixId());
        createConf.setIceId(iceId);
        createConf.setConfId(createConf.getId());
        saveConfUpdate(createConf, iceId);
        update(operateConf, iceId);
        return createConf.getMixId();
    }

    private Long delete(IceEditNode editNode) {
        int app = editNode.getApp();
        long iceId = editNode.getIceId();

        if (editNode.getParentId() != null) {
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
            return operateConf.getMixId();
        }

        if (editNode.getNextId() != null) {
            IceConf operateConf = iceServerService.getMixConfById(app, editNode.getNextId(), iceId);
            if (operateConf == null) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "nextId", editNode.getNextId());
            }
            if (operateConf.getForwardId() == null || !operateConf.getForwardId().equals(editNode.getSelectId())) {
                throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "nextId:" + editNode.getNextId() + " not have this forward:" + editNode.getSelectId());
            }
            operateConf.setForwardId(null);
            update(operateConf, iceId);
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
            String[] sonIdStrs = editNode.getMultiplexIds().split(Constant.REGEX_COMMA);
            List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
            Set<Long> sonIdSet = new HashSet<>(sonIdStrs.length);
            for (String sonIdStr : sonIdStrs) {
                Long sonId = Long.valueOf(sonIdStr);
                sonIdList.add(sonId);
                sonIdSet.add(sonId);
            }
            if (iceServerService.haveCircle(app, iceId, operateConf.getMixId(), sonIdList)) {
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
            return operateConf.getMixId();
        }

        IceConf createConf = createNewConf(editNode, app);
        operateConf.setSonIds(!StringUtils.hasLength(operateConf.getSonIds()) ?
                String.valueOf(createConf.getMixId()) :
                operateConf.getSonIds() + Constant.REGEX_COMMA + createConf.getMixId());
        createConf.setIceId(iceId);
        createConf.setConfId(createConf.getId());
        saveConfUpdate(createConf, iceId);
        update(operateConf, iceId);
        return createConf.getMixId();
    }

    private IceConf createNewConf(IceEditNode editNode, int app) {
        try {
            IceConf createConf = new IceConf();
            createConf.setId(storageService.nextConfId(app));
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
            }
            createConf.setUpdateAt(new Date());
            createConf.setCreateAt(new Date());
            createConf.setStatus(IceStorageConstants.STATUS_ONLINE);

            // 不再创建confs占位，新建节点只存在于updates中
            // 发布时才真正写入confs

            return createConf;
        } catch (IOException e) {
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void saveConfUpdate(IceConf conf, long iceId) {
        try {
            IceConfDto dto = conf.toDto();
            dto.setIceId(iceId);
            dto.setConfId(conf.getId());
            dto.setStatus(IceStorageConstants.STATUS_ONLINE);
            storageService.saveConfUpdate(conf.getApp(), iceId, dto);
        } catch (IOException e) {
            throw new ErrorCodeException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    private void update(IceConf operateConf, long iceId) {
        operateConf.setUpdateAt(new Date());
        if (!operateConf.isUpdatingConf()) {
            operateConf.setIceId(iceId);
            operateConf.setConfId(operateConf.getId());
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
    public List<IceLeafClass> getConfLeafClass(int app, byte type) {
        List<IceLeafClass> result = new ArrayList<>();
        Map<String, LeafNodeInfo> clientClazzInfoMap = clientManager.getLeafTypeClasses(app, type);
        if (!CollectionUtils.isEmpty(clientClazzInfoMap)) {
            for (Map.Entry<String, LeafNodeInfo> entry : clientClazzInfoMap.entrySet()) {
                LeafNodeInfo nodeInfo = entry.getValue();
                IceLeafClass leafClass = new IceLeafClass();
                leafClass.setFullName(entry.getKey());
                leafClass.setName(nodeInfo.getName());
                leafClass.setOrder(nodeInfo.getOrder() != null ? nodeInfo.getOrder() : 100);
                result.add(leafClass);
            }
            // 按order升序排列
            result.sort(Comparator.comparingInt(IceLeafClass::getOrder));
        }
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

    private LeafNodeInfo confClazzCheck(int app, String clazz, byte type) {
        Map<String, LeafNodeInfo> clazzInfoMap = clientManager.getLeafTypeClasses(app, type);
        if (!CollectionUtils.isEmpty(clazzInfoMap)) {
            return clazzInfoMap.get(clazz);
        }
        // 没有可用客户端时，允许手动输入class
        return null;
    }

    @Override
    public IceShowConf confDetail(int app, long confId, String address, long iceId) {
        if (address == null || address.equals("server")) {
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
        // 不再支持从client获取配置详情
        throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "client address not supported anymore, use 'server' instead");
    }

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
}

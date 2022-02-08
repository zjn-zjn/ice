package com.ice.server.service.impl;

import com.alibaba.fastjson.JSONValidator;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.server.dao.mapper.IceBaseMapper;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.mapper.IcePushHistoryMapper;
import com.ice.server.dao.model.*;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.*;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceEditService;
import com.ice.server.service.IceServerService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author zjn
 */
@Service
@Deprecated
public class IceEditServiceImpl implements IceEditService {

    @Resource
    private IceBaseMapper baseMapper;

    @Resource
    private IceConfMapper confMapper;

    @Resource
    private IcePushHistoryMapper pushHistoryMapper;

    @Resource
    private IceServerService iceServerService;

    @Resource
    private AmqpTemplate amqpTemplate;

    @Resource
    private IceConfService iceConfService;

    public static boolean isRelation(IceConfVo dto) {
        return isRelation(dto.getNodeType());
    }

    public static boolean isRelation(Byte type) {
        return type == NodeTypeEnum.NONE.getType() || type == NodeTypeEnum.ALL.getType()
                || type == NodeTypeEnum.AND.getType() || type == NodeTypeEnum.TRUE.getType()
                || type == NodeTypeEnum.ANY.getType();
    }

    /*
     * get Base
     */
    @Override
    public WebResult getBase(Integer app, Integer pageIndex, Integer pageSize, Long id, String name, String scene) {
        if (id != null) {
            IceBase base = baseMapper.selectByPrimaryKey(id);
            if (base != null) {
                return new WebResult(Collections.singletonList(base));
            }
        }
        IceBaseExample example = new IceBaseExample();
        IceBaseExample.Criteria criteria = example.createCriteria();
        if (StringUtils.hasLength(name)) {
            criteria.andNameLike(name + "%");
        }
        if (StringUtils.hasLength(scene)) {
            criteria.andScenesFindInSet(scene);
        }
        criteria.andAppEqualTo(app);
        example.setOrderByClause("update_at desc");
        Page<IcePushHistory> startPage = PageMethod.startPage(pageIndex, pageSize);
        return new WebResult<>(baseMapper.selectByExample(example));
    }

    /*
     * 编辑base
     */
    @Override
    @Transactional
    public WebResult editBase(IceBaseVo baseVo) {
        WebResult result = new WebResult<>();
        if (baseVo == null) {
            result.setRet(-1);
            result.setMsg("入参为空");
            return result;
        }
        if (baseVo.getId() == null) {
            /*新增的需要在conf里新建一个root root默认是none*/
            if (baseVo.getConfId() == null) {
                IceConf createConf = new IceConf();
                createConf.setApp(baseVo.getApp());
                createConf.setType(NodeTypeEnum.NONE.getType());
                createConf.setUpdateAt(new Date());
                confMapper.insertSelective(createConf);
                baseVo.setConfId(createConf.getId());
            }
            IceBase createBase = convert(baseVo);
            createBase.setConfId(baseVo.getConfId());
            createBase.setUpdateAt(new Date());
            baseMapper.insertSelective(createBase);
            return result;
        } else {
            /*编辑*/
            IceBaseExample example = new IceBaseExample();
            example.createCriteria().andIdEqualTo(baseVo.getId()).andAppEqualTo(baseVo.getApp());
            baseMapper.updateByExampleSelective(convert(baseVo), example);
        }
        return result;
    }

    /*
     * 编辑Conf
     */
    @Override
    @Transactional
    public EditResult editConf(Integer app, Integer type, Long iceId, IceConfVo confVo) {
        EditResult result = new EditResult();
        TimeTypeEnum typeEnum = TimeTypeEnum.getEnum(confVo.getTimeType());
        if (typeEnum == null) {
            confVo.setTimeType(TimeTypeEnum.NONE.getType());
            typeEnum = TimeTypeEnum.NONE;
        }
        switch (typeEnum) {
            case NONE:
                confVo.setStart(null);
                confVo.setEnd(null);
                break;
            case AFTER_START:
                if (confVo.getStart() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start null");
                }
                confVo.setEnd(null);
                break;
            case BEFORE_END:
                if (confVo.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "end null");
                }
                confVo.setStart(null);
                break;
            case BETWEEN:
                if (confVo.getStart() == null || confVo.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start|end null");
                }
                break;
        }
        if (confVo.getNodeType() != null && NodeTypeEnum.isRelation(confVo.getNodeType())) {
            confVo.setConfName(null);
            confVo.setConfField(null);
        }
        switch (type) {
            case 1://新建
                /*新建*/
                if (confVo.getOperateNodeId() == null) {
                    /*新建根节点*/
//                    IceBaseExample baseExample = new IceBaseExample();
//                    baseExample.createCriteria().andAppEqualTo(app).andIdEqualTo(iceId);
//                    List<IceBase> baseList = baseMapper.selectByExample(baseExample);
//                    if (!CollectionUtils.isEmpty(baseList)) {
//                        IceBase base = baseList.get(0);
//                        Long rootId = base.getConfId() > 0 ? base.getConfId() : -base.getConfId();
//                        IceConfExample confExample = new IceConfExample();
//                        confExample.createCriteria().andIdEqualTo(rootId);
//                        IceConf conf = new IceConf();
//                        conf.setDebug(confVo.getDebug() ? (byte) 1 : (byte) 0);
//                        conf.setTimeType(confVo.getTimeType());
//                        conf.setStart(confVo.getStart() == null ? null : new Date(confVo.getStart()));
//                        conf.setEnd(confVo.getEnd() == null ? null : new Date(confVo.getEnd()));
//                        conf.setInverse(confVo.getInverse() ? (byte) 1 : (byte) 0);
//                        conf.setType(confVo.getNodeType());
//                        conf.setName(StringUtils.isEmpty(confVo.getName()) ? "" : confVo.getName());
//                        if (!isRelation(confVo)) {
//                            conf.setConfName(confVo.getConfName());
//                            conf.setConfField(StringUtils.isEmpty(confVo.getConfField()) ? "{}" : confVo.getConfField());
//                        }
//                        conf.setUpdateAt(new Date());
//                        confMapper.updateByExampleSelective(conf, confExample);
//                        base.setConfId(rootId);
//                        base.setUpdateAt(new Date());
//                        baseMapper.updateByExampleSelective(base, baseExample);
//                    }
                } else {
                    IceConf operateConf = confMapper.selectByPrimaryKey(confVo.getOperateNodeId());
                    if (operateConf != null) {
                        if (!isRelation(operateConf.getType())) {
                            result.setCode(-1);
                            result.setMsg("only relation can have son");
                            return result;
                        }
                        if (confVo.getNodeId() != null) {
                            /*从已知节点ID添加*/
                            String[] sonIdStrs = confVo.getNodeId().split(",");
                            List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
                            Set<Long> sonIdSet = new HashSet<>(sonIdStrs.length);
                            for (String sonIdStr : sonIdStrs) {
                                Long sonId = Long.valueOf(sonIdStr);
                                sonIdList.add(sonId);
                                sonIdSet.add(sonId);
                            }
                            if (iceServerService.haveCircle(operateConf.getId(), sonIdList)) {
                                result.setCode(-1);
                                result.setMsg("have circle");
                                return result;
                            }
                            IceConfExample example = new IceConfExample();
                            example.createCriteria().andAppEqualTo(app).andIdIn(sonIdSet);
                            List<IceConf> children = confMapper.selectByExample(example);
                            if (CollectionUtils.isEmpty(children) || children.size() != sonIdSet.size()) {
                                result.setCode(-1);
                                result.setMsg("one of son id not exist:" + confVo.getNodeId());
                                return result;
                            }
                            operateConf.setSonIds(StringUtils.isEmpty(operateConf.getSonIds()) ?
                                    String.valueOf(confVo.getNodeId()) :
                                    operateConf.getSonIds() + "," + confVo.getNodeId());
                            result.setNodeId(operateConf.getId());
                            result.setLinkIds(sonIdList);
                        } else {
                            IceConf createConf = new IceConf();
                            createConf.setDebug(confVo.getDebug() ? (byte) 1 : (byte) 0);
                            createConf.setInverse(confVo.getInverse() ? (byte) 1 : (byte) 0);
                            createConf.setTimeType(confVo.getTimeType());
                            createConf.setStart(confVo.getStart() == null ? null : new Date(confVo.getStart()));
                            createConf.setEnd(confVo.getEnd() == null ? null : new Date(confVo.getEnd()));
                            createConf.setApp(app);
                            createConf.setType(confVo.getNodeType());
                            createConf.setName(StringUtils.isEmpty(confVo.getName()) ? "" : confVo.getName());
                            if (!isRelation(confVo)) {
                                if (StringUtils.hasLength(confVo.getConfField())) {
                                    JSONValidator validator = JSONValidator.from(confVo.getConfField());
                                    if (!validator.validate()) {
                                        result.setMsg("confFiled json illegal");
                                        result.setCode(-1);
                                        return result;
                                    }
                                }
                                try {
                                    iceConfService.leafClassCheck(app, confVo.getConfName(), confVo.getNodeType());
                                } catch (Exception e) {
                                    result.setCode(-1);
                                    result.setMsg(e.getMessage());
                                    return result;
                                }
                                createConf.setConfName(confVo.getConfName());
                                createConf.setConfField(confVo.getConfField());
                            }
                            createConf.setUpdateAt(new Date());
                            confMapper.insertSelective(createConf);
                            operateConf.setSonIds(StringUtils.isEmpty(operateConf.getSonIds()) ?
                                    String.valueOf(createConf.getId()) :
                                    operateConf.getSonIds() + "," + createConf.getId());
                            result.setNodeId(operateConf.getId());
                            result.setLinkId(createConf.getId());
                        }
                        operateConf.setUpdateAt(new Date());
                        confMapper.updateByPrimaryKey(operateConf);
                    }
                }
                break;
            case 2://编辑
                if (confVo.getOperateNodeId() != null) {
                    IceConf operateConf = confMapper.selectByPrimaryKey(confVo.getOperateNodeId());
                    if (operateConf != null) {
                        operateConf.setDebug(confVo.getDebug() ? (byte) 1 : (byte) 0);
                        operateConf.setTimeType(confVo.getTimeType());
                        operateConf.setStart(confVo.getStart() == null ? null : new Date(confVo.getStart()));
                        operateConf.setEnd(confVo.getEnd() == null ? null : new Date(confVo.getEnd()));
                        operateConf.setInverse(confVo.getInverse() ? (byte) 1 : (byte) 0);
                        operateConf.setName(StringUtils.isEmpty(confVo.getName()) ? "" : confVo.getName());
                        if (!isRelation(confVo)) {
                            if (StringUtils.hasLength(confVo.getConfField())) {
                                JSONValidator validator = JSONValidator.from(confVo.getConfField());
                                if (!validator.validate()) {
                                    result.setMsg("confFiled json illegal");
                                    result.setCode(-1);
                                    return result;
                                }
                            }
                            operateConf.setConfField(confVo.getConfField());
                        }
                        operateConf.setUpdateAt(new Date());
                        confMapper.updateByPrimaryKey(operateConf);
                    }
                }
                break;
            case 3://删除
                if (confVo.getOperateNodeId() != null) {
                    if (confVo.getParentId() != null) {
                        IceConf operateConf = confMapper.selectByPrimaryKey(confVo.getParentId());
                        if (operateConf != null) {
                            String sonIdStr = operateConf.getSonIds();
                            if (StringUtils.hasLength(sonIdStr)) {
                                String[] sonIdStrs = sonIdStr.split(",");
                                StringBuilder sb = new StringBuilder();
                                for (String idStr : sonIdStrs) {
                                    if (!confVo.getOperateNodeId().toString().equals(idStr)) {
                                        sb.append(idStr).append(",");
                                    }
                                }
                                String str = sb.toString();
                                if (StringUtils.isEmpty(str)) {
                                    operateConf.setSonIds("");
                                } else {
                                    operateConf.setSonIds(str.substring(0, str.length() - 1));
                                }
                                result.setNodeId(confVo.getParentId());
                                result.setUnLinkId(confVo.getOperateNodeId());
                                operateConf.setUpdateAt(new Date());
                                confMapper.updateByPrimaryKey(operateConf);
                            }
                        }
                    } else if (confVo.getNextId() != null) {
                        IceConf operateConf = confMapper.selectByPrimaryKey(confVo.getNextId());
                        if (operateConf != null) {
                            /*多校验一步*/
                            if (operateConf.getForwardId() != null && operateConf.getForwardId().equals(confVo.getOperateNodeId())) {
                                operateConf.setForwardId(null);
                                operateConf.setUpdateAt(new Date());
                                result.setNodeId(confVo.getNextId());
                                result.setUnLinkId(confVo.getOperateNodeId());
                                confMapper.updateByPrimaryKey(operateConf);
                            }
                        }
                    } /*else {
                     *//*该节点没有父节点和next 判断为根节点 根节点删除直接将base表中confId变成负数,避免只是为了改变根节点类型而直接全部删除重做*//*
                        IceBaseExample baseExample = new IceBaseExample();
                        baseExample.createCriteria().andAppEqualTo(app).andIdEqualTo(iceId);
                        List<IceBase> baseList = baseMapper.selectByExample(baseExample);
                        if (!CollectionUtils.isEmpty(baseList)) {
                            IceBase base = baseList.get(0);
                            if (base.getConfId().equals(confVo.getOperateNodeId())) {
                                *//*校验相等再变更*//*
                                base.setConfId(-confVo.getOperateNodeId());
                                base.setUpdateAt(new Date());
                                baseMapper.updateByExampleSelective(base, baseExample);
                            }
                        }
                    }*/
                }
                break;
            case 4://新增前置
                if (confVo.getOperateNodeId() != null) {
                    IceConf operateConf = confMapper.selectByPrimaryKey(confVo.getOperateNodeId());
                    if (operateConf != null) {
                        if (operateConf.getForwardId() != null) {
                            result.setCode(-1);
                            result.setMsg("already have forward");
                            return result;
                        }
                        if (confVo.getNodeId() != null) {
                            /*从已知节点ID添加*/
                            Long forwardId = Long.valueOf(confVo.getNodeId());
                            if (iceServerService.haveCircle(operateConf.getId(), forwardId)) {
                                result.setCode(-1);
                                result.setMsg("have circle");
                                return result;
                            }
                            IceConf forward = confMapper.selectByPrimaryKey(forwardId);
                            if (forward == null) {
                                result.setCode(-1);
                                result.setMsg("id not exist:" + confVo.getNodeId());
                                return result;
                            }
                            operateConf.setForwardId(forwardId);
                            result.setNodeId(operateConf.getId());
                            result.setLinkId(forwardId);
                        } else {
                            IceConf createConf = new IceConf();
                            createConf.setDebug(confVo.getDebug() ? (byte) 1 : (byte) 0);
                            createConf.setInverse(confVo.getInverse() ? (byte) 1 : (byte) 0);
                            createConf.setTimeType(confVo.getTimeType());
                            createConf.setStart(confVo.getStart() == null ? null : new Date(confVo.getStart()));
                            createConf.setEnd(confVo.getEnd() == null ? null : new Date(confVo.getEnd()));
                            createConf.setType(confVo.getNodeType());
                            createConf.setApp(app);
                            createConf.setName(StringUtils.isEmpty(confVo.getName()) ? "" : confVo.getName());
                            if (!isRelation(confVo)) {
                                if (StringUtils.hasLength(confVo.getConfField())) {
                                    JSONValidator validator = JSONValidator.from(confVo.getConfField());
                                    if (!validator.validate()) {
                                        result.setMsg("confFiled json illegal");
                                        result.setCode(-1);
                                        return result;
                                    }
                                }
                                try {
                                    iceConfService.leafClassCheck(app, confVo.getConfName(), confVo.getNodeType());
                                } catch (Exception e) {
                                    result.setCode(-1);
                                    result.setMsg(e.getMessage());
                                    return result;
                                }
                                createConf.setConfName(confVo.getConfName());
                                createConf.setConfField(confVo.getConfField());
                            }
                            createConf.setUpdateAt(new Date());
                            confMapper.insertSelective(createConf);
                            operateConf.setForwardId(createConf.getId());
                            result.setNodeId(operateConf.getId());
                            result.setLinkId(createConf.getId());
                        }
                        operateConf.setUpdateAt(new Date());
                        confMapper.updateByPrimaryKey(operateConf);
                    }
                }
                break;
            case 5:
                /*转换节点*/
                if (confVo.getOperateNodeId() != null) {
                    if (confVo.getNodeId() != null) {
                        /*把自己直接换成其他id 父节点的该子节点更换即可 */
                        if (confVo.getParentId() == null && confVo.getNextId() == null) {
                            /*根节点更换 涉及base更换*/
                            result.setMsg("root can not exchange by id");
                            result.setCode(-1);
                            return result;
//                            IceBaseExample baseExample = new IceBaseExample();
//                            baseExample.createCriteria().andConfIdEqualTo(confVo.getOperateNodeId()).andAppEqualTo(app);
//                            List<IceBase> baseList = baseMapper.selectByExample(baseExample);
//                            if (!CollectionUtils.isEmpty(baseList)) {
//                                IceBase base = baseList.get(0);
//                                base.setConfId(Long.valueOf(confVo.getNodeId()));
//                                base.setUpdateAt(new Date());
//                                baseMapper.updateByExampleSelective(base, baseExample);
//                            }
                        } else if (confVo.getParentId() != null) {
                            IceConf conf = confMapper.selectByPrimaryKey(confVo.getParentId());
                            if (conf != null) {
                                String[] sonIdStrs = confVo.getNodeId().split(",");
                                List<Long> sonIdList = new ArrayList<>(sonIdStrs.length);
                                for (String sonIdStr : sonIdStrs) {
                                    Long sonId = Long.valueOf(sonIdStr);
                                    sonIdList.add(sonId);
                                }
                                if (iceServerService.haveCircle(confVo.getParentId(), sonIdList)) {
                                    result.setCode(-1);
                                    result.setMsg("have circle");
                                    return result;
                                }
                                String[] sonIds = conf.getSonIds().split(",");
                                StringBuilder sb = new StringBuilder();
                                boolean hasChange = false;
                                for (String sonIdStr : sonIds) {
                                    Long sonId = Long.valueOf(sonIdStr);
                                    if (!hasChange && confVo.getOperateNodeId().equals(sonId)) {
                                        /*相同的更换掉 FIXME 默认换掉第一个*/
                                        sb.append(confVo.getNodeId()).append(",");
                                        hasChange = true;
                                    } else {
                                        sb.append(sonIdStr).append(",");
                                    }
                                }
                                result.setNodeId(confVo.getParentId());
                                result.setUnLinkId(confVo.getOperateNodeId());
                                result.setLinkIds(sonIdList);
                                conf.setSonIds(sb.substring(0, sb.length() - 1));
                                conf.setUpdateAt(new Date());
                                confMapper.updateByPrimaryKey(conf);
                            }
                        } else if (confVo.getNextId() != null) {
                            /*更换前置节点*/
                            IceConf conf = confMapper.selectByPrimaryKey(confVo.getNextId());
                            if (conf != null) {
                                Long forwardId = conf.getId();
                                if (forwardId == null) {
                                    result.setCode(-1);
                                    result.setMsg("forward不存在");
                                    return result;
                                }
                                Long exchangeForwardId = Long.parseLong(confVo.getNodeId());
                                if (iceServerService.haveCircle(confVo.getNextId(), exchangeForwardId)) {
                                    result.setCode(-1);
                                    result.setMsg("have circle");
                                    return result;
                                }
                                result.setNodeId(confVo.getNextId());
                                result.setUnLinkId(confVo.getOperateNodeId());
                                result.setLinkId(exchangeForwardId);
                                conf.setForwardId(exchangeForwardId);
                                conf.setUpdateAt(new Date());
                                confMapper.updateByPrimaryKey(conf);
                            }
                        }
                    } else {
                        /*正常的更换,把所有参数换一遍*/
                        IceConf operateConf = confMapper.selectByPrimaryKey(confVo.getOperateNodeId());
                        if (operateConf != null) {
                            operateConf.setDebug(confVo.getDebug() ? (byte) 1 : (byte) 0);
                            operateConf.setInverse(confVo.getInverse() ? (byte) 1 : (byte) 0);
                            operateConf.setTimeType(confVo.getTimeType());
                            operateConf.setStart(confVo.getStart() == null ? null : new Date(confVo.getStart()));
                            operateConf.setEnd(confVo.getEnd() == null ? null : new Date(confVo.getEnd()));
                            operateConf.setType(confVo.getNodeType());
                            if (!isRelation(confVo.getNodeType())) {
                                operateConf.setSonIds(null);
                            }
                            operateConf.setApp(app);
                            operateConf.setName(StringUtils.isEmpty(confVo.getName()) ? "" : confVo.getName());
                            if (!isRelation(confVo)) {
                                if (StringUtils.hasLength(confVo.getConfField())) {
                                    JSONValidator validator = JSONValidator.from(confVo.getConfField());
                                    if (!validator.validate()) {
                                        result.setMsg("confFiled json illegal");
                                        result.setCode(-1);
                                        return result;
                                    }
                                }
                                try {
                                    iceConfService.leafClassCheck(app, confVo.getConfName(), confVo.getNodeType());
                                } catch (Exception e) {
                                    result.setCode(-1);
                                    result.setMsg(e.getMessage());
                                    return result;
                                }
                                operateConf.setConfName(confVo.getConfName());
                                operateConf.setConfField(confVo.getConfField());
                            } else {
                                operateConf.setConfName(null);
                                operateConf.setConfField(null);
                            }
                            operateConf.setUpdateAt(new Date());
                            confMapper.updateByPrimaryKey(operateConf);
                        }
                    }
                }
                break;
            case 6:
                /*上移节点*/
                if (confVo.getOperateNodeId() != null) {
                    if (confVo.getParentId() != null) {
                        if (confVo.getParentId() != null) {
                            IceConf conf = confMapper.selectByPrimaryKey(confVo.getParentId());
                            if (conf != null) {
                                if (StringUtils.hasLength(conf.getSonIds())) {
                                    String[] sonIds = conf.getSonIds().split(",");
                                    if (sonIds.length <= 1) {
                                        break;
                                    }
                                    int index = -1;
                                    for (int i = 0; i < sonIds.length; i++) {
                                        if (Long.valueOf(sonIds[i]).equals(confVo.getOperateNodeId())) {
                                            index = i;
                                            break;
                                        }
                                    }
                                    if (index != -1 && index != 0) {
                                        String tmp = sonIds[index];
                                        sonIds[index] = sonIds[index - 1];
                                        sonIds[index - 1] = tmp;
                                    }
                                    StringBuilder sb = new StringBuilder();
                                    for (String sonIdStr : sonIds) {
                                        sb.append(sonIdStr).append(",");
                                    }
                                    conf.setSonIds(sb.substring(0, sb.length() - 1));
                                    conf.setUpdateAt(new Date());
                                    confMapper.updateByPrimaryKey(conf);
                                }
                            }
                        }
                    }
                }
                break;
            case 7:
                /*下移节点*/
                if (confVo.getOperateNodeId() != null) {
                    if (confVo.getParentId() != null) {
                        if (confVo.getParentId() != null) {
                            IceConf conf = confMapper.selectByPrimaryKey(confVo.getParentId());
                            if (conf != null) {
                                if (StringUtils.hasLength(conf.getSonIds())) {
                                    String[] sonIds = conf.getSonIds().split(",");
                                    if (sonIds.length <= 1) {
                                        break;
                                    }
                                    int index = -1;
                                    for (int i = 0; i < sonIds.length; i++) {
                                        if (Long.valueOf(sonIds[i]).equals(confVo.getOperateNodeId())) {
                                            index = i;
                                            break;
                                        }
                                    }
                                    if (index != -1 && index != sonIds.length) {
                                        String tmp = sonIds[index];
                                        sonIds[index] = sonIds[index + 1];
                                        sonIds[index + 1] = tmp;
                                    }
                                    StringBuilder sb = new StringBuilder();
                                    for (String sonIdStr : sonIds) {
                                        sb.append(sonIdStr).append(",");
                                    }
                                    conf.setSonIds(sb.substring(0, sb.length() - 1));
                                    conf.setUpdateAt(new Date());
                                    confMapper.updateByPrimaryKey(conf);
                                }
                            }
                        }
                    }
                }
                break;
            default:
                return result;
        }
        return result;
    }

    /*
     * 获取leafClass
     */
    @Override
    public WebResult getLeafClass(int app, byte type) {
        WebResult<List> result = new WebResult<>();
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
        result.setData(list);
        return result;
    }

//    /*
//     * 发布
//     */
//    @Override
//    public WebResult push(Integer app, Long iceId, String reason) {
//        WebResult<Long> result = new WebResult<>();
//        IceBaseExample baseExample = new IceBaseExample();
//        baseExample.createCriteria().andIdEqualTo(iceId);
//        List<IceBase> baseList = baseMapper.selectByExample(baseExample);
//        if (CollectionUtils.isEmpty(baseList)) {
//            result.setRet(-1);
//            result.setMsg("ice id " + iceId + "not exist");
//        }
//        IceBase base = baseList.get(0);
//        base.setUpdateAt(new Date());
//        if (base.getScenes() == null) {
//            base.setScenes("");
//        }
//        PushData pushData = new PushData();
//        pushData.setBase(base);
//        Long confId = base.getConfId();
//        Object obj = amqpTemplate.convertSendAndReceive(Constant.getShowConfExchange(), String.valueOf(base.getApp()),
//                String.valueOf(iceId));
//        if (obj != null) {
//            String json = (String) obj;
//            if (StringUtils.hasLength(json)) {
//                Map map = JSON.parseObject(json, Map.class);
//                if (!CollectionUtils.isEmpty(map)) {
//                    Map handlerMap = (Map) map.get("handler");
//                    if (!CollectionUtils.isEmpty(handlerMap)) {
//                        Map rootMap = (Map) handlerMap.get("root");
//                        if (!CollectionUtils.isEmpty(rootMap)) {
//                            Set<Long> allIdSet = new HashSet<>();
//                            findAllConfIds(rootMap, allIdSet);
//                            if (!CollectionUtils.isEmpty(allIdSet)) {
//                                IceConfExample confExample = new IceConfExample();
//                                confExample.createCriteria().andIdIn(new ArrayList<>(allIdSet));
//                                List<IceConf> iceConfs = confMapper.selectByExample(confExample);
//                                if (!CollectionUtils.isEmpty(iceConfs)) {
//                                    for (IceConf conf : iceConfs) {
//                                        conf.setUpdateAt(new Date());
//                                        if (isRelation(conf.getType()) && conf.getSonIds() == null) {
//                                            conf.setSonIds("");
//                                        }
//                                    }
//                                    pushData.setConfs(iceConfs);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        IcePushHistory history = new IcePushHistory();
//        history.setApp(base.getApp());
//        history.setIceId(iceId);
//        history.setReason(reason);
//        history.setOperator("zjn");
//        history.setPushData(JSON.toJSONString(pushData));
//        pushHistoryMapper.insertSelective(history);
//        result.setData(history.getId());
//        return result;
//    }

    /*
     * 发布历史
     */
    @Override
    public WebResult history(Integer app, Long iceId) {
        WebResult<List> result = new WebResult<>();
        IcePushHistoryExample example = new IcePushHistoryExample();
        example.createCriteria().andAppEqualTo(app).andIceIdEqualTo(iceId);
        example.setOrderByClause("create_at desc limit 100");
        result.setData(pushHistoryMapper.selectByExample(example));
        return result;
    }

    @Override
    public WebResult deleteHistory(Long pushId) {
        pushHistoryMapper.deleteByPrimaryKey(pushId);
        return WebResult.success();
    }
//
//    /*
//     * 导出数据
//     */
//    @Override
//    public WebResult exportData(Long iceId, Long pushId) {
//        WebResult<String> result = new WebResult<>();
//        if (pushId != null && pushId > 0) {
//            IcePushHistoryExample historyExample = new IcePushHistoryExample();
//            historyExample.createCriteria().andIdEqualTo(pushId);
//            List<IcePushHistory> histories = pushHistoryMapper.selectByExampleWithBLOBs(historyExample);
//            if (!CollectionUtils.isEmpty(histories)) {
//                result.setData(histories.get(0).getPushData());
//                return result;
//            }
//            result.setRet(-1);
//            result.setMsg("pushId不存在");
//            return result;
//        }
//        IceBaseExample baseExample = new IceBaseExample();
//        baseExample.createCriteria().andIdEqualTo(iceId);
//        List<IceBase> baseList = baseMapper.selectByExample(baseExample);
//        if (CollectionUtils.isEmpty(baseList)) {
//            result.setRet(-1);
//            result.setMsg("iceId不存在");
//            return result;
//        }
//        IceBase base = baseList.get(0);
//        base.setUpdateAt(new Date());
//        if (base.getScenes() == null) {
//            base.setScenes("");
//        }
//        PushData pushData = new PushData();
//        pushData.setBase(base);
//        Long confId = base.getConfId();
//        Object obj = amqpTemplate.convertSendAndReceive(Constant.getShowConfExchange(), String.valueOf(base.getApp()),
//                String.valueOf(iceId));
//        if (obj != null) {
//            String json = (String) obj;
//            if (StringUtils.hasLength(json)) {
//                Map map = JSON.parseObject(json, Map.class);
//                if (!CollectionUtils.isEmpty(map)) {
//                    Map handlerMap = (Map) map.get("handler");
//                    if (!CollectionUtils.isEmpty(handlerMap)) {
//                        Map rootMap = (Map) handlerMap.get("root");
//                        if (!CollectionUtils.isEmpty(rootMap)) {
//                            Set<Long> allIdSet = new HashSet<>();
//                            findAllConfIds(rootMap, allIdSet);
//                            if (!CollectionUtils.isEmpty(allIdSet)) {
//                                IceConfExample confExample = new IceConfExample();
//                                confExample.createCriteria().andIdIn(new ArrayList<>(allIdSet));
//                                List<IceConf> iceConfs = confMapper.selectByExample(confExample);
//                                if (!CollectionUtils.isEmpty(iceConfs)) {
//                                    for (IceConf conf : iceConfs) {
//                                        conf.setUpdateAt(new Date());
//                                        if (isRelation(conf.getType()) && conf.getSonIds() == null) {
//                                            conf.setSonIds("");
//                                        }
//                                    }
//                                    pushData.setConfs(iceConfs);
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        result.setData(JSON.toJSONString(pushData));
//        return result;
//    }
//
//    /*
//     * 回滚
//     */
//    @Override
//    @Transactional
//    public WebResult rollback(Long pushId) {
//        WebResult result = new WebResult<>();
//        if (pushId != null && pushId > 0) {
//            IcePushHistoryExample historyExample = new IcePushHistoryExample();
//            historyExample.createCriteria().andIdEqualTo(pushId);
//            List<IcePushHistory> histories = pushHistoryMapper.selectByExampleWithBLOBs(historyExample);
//            if (!CollectionUtils.isEmpty(histories)) {
//                importData(histories.get(0).getPushData());
//                return result;
//            }
//            result.setRet(-1);
//            result.setMsg("pushId不存在");
//        }
//        return result;
//    }

//    private void findAllConfIds(Map map, Set<Long> ids) {
//        Long nodeId = (Long) map.get("iceNodeId");
//        if (nodeId != null) {
//            ids.add(nodeId);
//        }
//        Map forward = (Map) map.get("iceForward");
//        if (forward != null) {
//            findAllConfIds(forward, ids);
//        }
//        List<Map> children = getChild(map);
//        if (CollectionUtils.isEmpty(children)) {
//            return;
//        }
//        for (Map child : children) {
//            findAllConfIds(child, ids);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    private List<Map> getChild(Map map) {
//        return (List) map.get("children");
//    }

    /*
     * 导入数据
     */
//    @Override
//    @Transactional
//    public WebResult importData(String data) {
//        WebResult result = new WebResult<>();
//        PushData pushData = JSON.parseObject(data, PushData.class);
//        IceBase base = pushData.getBase();
//        List<IceConf> confs = pushData.getConfs();
//        if (!CollectionUtils.isEmpty(confs)) {
//            for (IceConf conf : confs) {
//                IceConfExample confExample = new IceConfExample();
//                confExample.createCriteria().andIdEqualTo(conf.getId());
//                List<IceConf> confList = confMapper.selectByExample(confExample);
//                if (CollectionUtils.isEmpty(confList)) {
//                    conf.setCreateAt(null);
//                    conf.setUpdateAt(new Date());
//                    confMapper.insertSelectiveWithId(conf);
//                } else {
//                    conf.setId(null);
//                    conf.setUpdateAt(new Date());
//                    confMapper.updateByExampleSelective(conf, confExample);
//                }
//            }
//        }
//        if (base != null) {
//            IceBaseExample baseExample = new IceBaseExample();
//            baseExample.createCriteria().andIdEqualTo(base.getId());
//            List<IceBase> baseList = baseMapper.selectByExample(baseExample);
//            if (CollectionUtils.isEmpty(baseList)) {
//                base.setCreateAt(null);
//                base.setUpdateAt(new Date());
//                baseMapper.insertSelectiveWithId(base);
//            } else {
//                base.setId(null);
//                base.setUpdateAt(new Date());
//                baseMapper.updateByExampleSelective(base, baseExample);
//            }
//        }
//        return result;
//    }

    private IceBase convert(IceBaseVo vo) {
        IceBase base = new IceBase();
        base.setId(vo.getId());
        base.setTimeType(vo.getTimeType());
        base.setApp(vo.getApp());
        base.setDebug(vo.getDebug() == null ? 0 : vo.getDebug());
        base.setName(vo.getName());
        base.setScenes(vo.getScenes() == null ? "" : vo.getScenes());
        base.setStart(vo.getStart());
        base.setEnd(vo.getEnd());
        base.setStatus(vo.getStatus());
        base.setConfId(vo.getConfId());
        base.setPriority(1L);
        base.setUpdateAt(new Date());
        return base;
    }
}

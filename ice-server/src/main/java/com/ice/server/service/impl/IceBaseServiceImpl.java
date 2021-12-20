package com.ice.server.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.StatusEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.server.constant.Constant;
import com.ice.server.dao.mapper.IceBaseMapper;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.mapper.IcePushHistoryMapper;
import com.ice.server.dao.model.*;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class IceBaseServiceImpl implements IceBaseService {

    @Resource
    private IceBaseMapper iceBaseMapper;

    @Resource
    private IceConfMapper iceConfMapper;

    @Resource
    private IcePushHistoryMapper pushHistoryMapper;

    @Resource
    private IceServerService iceServerService;

    @Resource
    private AmqpTemplate amqpTemplate;

    @Override
    public PageResult<IceBase> baseList(IceBaseSearch search) {
        Page<IceBase> page = PageMethod.startPage(search.getPageNum(), search.getPageSize());
        iceBaseMapper.selectByExample(searchToExample(search));
        PageResult<IceBase> pageResult = new PageResult<>();
        pageResult.setList(page.getResult());
        pageResult.setTotal(page.getTotal());
        pageResult.setPages(page.getPages());
        pageResult.setPageNum(page.getPageNum());
        pageResult.setPageSize(page.getPageSize());
        return pageResult;
    }

    private IceBaseExample searchToExample(IceBaseSearch search) {
        IceBaseExample example = new IceBaseExample();
        example.setOrderByClause("update_at desc");
        IceBaseExample.Criteria criteria = example.createCriteria();
        criteria.andStatusEqualTo((byte) 1);
        if (search.getApp() != null) {
            criteria.andAppEqualTo(search.getApp());
        }
        if (search.getBaseId() != null) {
            criteria.andIdEqualTo(search.getBaseId());
            return example;
        }
        if (StringUtils.hasLength(search.getName())) {
            criteria.andNameLike(search.getName() + "%");
        }
        if (StringUtils.hasLength(search.getScene())) {
            criteria.andScenesFindInSet(search.getScene());
        }
        return example;
    }

    @Override
    @Transactional
    public Long baseEdit(IceBase base) {
        timeHandle(base);
        if (base.getId() == null) {
            /*for the new base, you need to create a new root in conf. the default root is none*/
            if (base.getConfId() == null) {
                IceConf createConf = new IceConf();
                createConf.setApp(base.getApp());
                createConf.setStatus(StatusEnum.ONLINE.getStatus());
                createConf.setType(NodeTypeEnum.NONE.getType());
                createConf.setUpdateAt(new Date());
                iceConfMapper.insertSelective(createConf);
                base.setConfId(createConf.getId());
            }
            base.setConfId(base.getConfId());
            iceBaseMapper.insertSelective(base);
            return base.getId();
        }
        iceBaseMapper.updateByPrimaryKey(base);
        return base.getId();
    }

    private static void timeHandle(IceBase base) {
        base.setUpdateAt(new Date());
        TimeTypeEnum typeEnum = TimeTypeEnum.getEnum(base.getTimeType());
        if (typeEnum == null) {
            base.setTimeType(TimeTypeEnum.NONE.getType());
            typeEnum = TimeTypeEnum.NONE;
        }
        switch (typeEnum) {
            case NONE:
                base.setStart(null);
                base.setEnd(null);
                break;
            case AFTER_START:
                if (base.getStart() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start null");
                }
                base.setEnd(null);
                break;
            case BEFORE_END:
                if (base.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "end null");
                }
                base.setStart(null);
                break;
            case BETWEEN:
                if (base.getStart() == null || base.getEnd() == null) {
                    throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "start|end null");
                }
                break;
        }
    }

    @Override
    @Transactional
    public Long push(Integer app, Long iceId, String reason) {
        IceBase base = iceBaseMapper.selectByPrimaryKey(iceId);
        if (base == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "iceId", iceId);
        }
        IcePushHistory history = new IcePushHistory();
        history.setApp(base.getApp());
        history.setIceId(iceId);
        history.setReason(reason);
        history.setOperator("zjn");
        history.setPushData(getPushDataJson(base));
        pushHistoryMapper.insertSelective(history);
        return history.getId();
    }

    private String getPushDataJson(IceBase base) {
        return JSON.toJSONString(getPushData(base));
    }

    private PushData getPushData(IceBase base) {
        PushData pushData = new PushData();
        pushData.setApp(base.getApp());
        pushData.setBase(Constant.baseToDtoWithName(base));
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getAllConfIdExchange(), String.valueOf(base.getApp()),
                String.valueOf(base.getId()));
        if (obj != null) {
            String json = (String) obj;
            if (StringUtils.hasLength(json)) {
                List<Long> allIds = JSON.parseArray(json, Long.class);
                if (!CollectionUtils.isEmpty(allIds)) {
                    IceConfExample confExample = new IceConfExample();
                    confExample.createCriteria().andAppEqualTo(base.getApp()).andIdIn(allIds);
                    List<IceConf> iceConfs = iceConfMapper.selectByExample(confExample);
                    pushData.setConfs(Constant.confListToDtoListWithName(iceConfs));
                }
            }
        }
        return pushData;
    }

    @Override
    public PageResult<IcePushHistory> history(Integer app, Long iceId, Integer pageNum, Integer pageSize) {
        IcePushHistoryExample example = new IcePushHistoryExample();
        example.createCriteria().andAppEqualTo(app).andIceIdEqualTo(iceId);
        example.setOrderByClause("create_at desc");
        Page<IcePushHistory> page = PageMethod.startPage(pageNum, pageSize);
        pushHistoryMapper.selectByExample(example);
        PageResult<IcePushHistory> pageResult = new PageResult<>();
        pageResult.setList(page.getResult());
        pageResult.setTotal(page.getTotal());
        pageResult.setPages(page.getPages());
        pageResult.setPageNum(page.getPageNum());
        pageResult.setPageSize(page.getPageSize());
        return pageResult;
    }

    @Override
    public String exportData(Long iceId, Long pushId) {
        if (pushId != null && pushId > 0) {
            IcePushHistory history = pushHistoryMapper.selectByPrimaryKey(pushId);
            if (history != null) {
                return history.getPushData();
            }
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "pushId", pushId);
        }
        IceBase base = iceBaseMapper.selectByPrimaryKey(iceId);
        if (base == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "iceId", iceId);
        }
        return getPushDataJson(base);
    }

    @Override
    public void rollback(Long pushId) {
        IcePushHistory history = pushHistoryMapper.selectByPrimaryKey(pushId);
        if (history == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "pushId", pushId);
        }
        importData(JSON.parseObject(history.getPushData(), PushData.class));
    }

    @Override
    @Transactional
    public void importData(PushData data) {
        Collection<IceConf> confs = Constant.dtoListToConfList(data.getConfs(), data.getApp());
        if (!CollectionUtils.isEmpty(confs)) {
            for (IceConf conf : confs) {
                IceConf oldConf = iceConfMapper.selectByPrimaryKey(conf.getId());
                conf.setUpdateAt(new Date());
                if (oldConf == null) {
                    iceConfMapper.insertWithId(conf);
                } else {
                    iceConfMapper.updateByPrimaryKey(conf);
                }
            }
        }
        IceBase base = Constant.dtoToBase(data.getBase(), data.getApp());
        if (base != null) {
            IceBase oldBase = iceBaseMapper.selectByPrimaryKey(base.getId());
            base.setUpdateAt(new Date());
            if (oldBase == null) {
                iceBaseMapper.insertWithId(base);
            } else {
                iceBaseMapper.updateByPrimaryKey(base);
            }
        }
    }
}

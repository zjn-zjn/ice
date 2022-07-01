package com.ice.server.service.impl;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ice.common.dto.IceTransferDto;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.utils.JacksonUtils;
import com.ice.server.constant.Constant;
import com.ice.server.dao.mapper.IceBaseMapper;
import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.mapper.IceConfUpdateMapper;
import com.ice.server.dao.mapper.IcePushHistoryMapper;
import com.ice.server.dao.model.*;
import com.ice.server.enums.StatusEnum;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;
import com.ice.server.nio.IceNioClientManager;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Set;

/**
 * @author waitmoon
 */
@Slf4j
@Service
public class IceBaseServiceImpl implements IceBaseService {

    @Resource
    private IceBaseMapper iceBaseMapper;

    @Resource
    private IceConfMapper iceConfMapper;

    @Resource
    private IceConfUpdateMapper iceConfUpdateMapper;

    @Resource
    private IcePushHistoryMapper pushHistoryMapper;

    @Resource
    private IceServerService iceServerService;

    @Resource
    private IceNioClientManager iceNioClientManager;

    @Resource
    private IceServerService serverService;

    @Resource
    private IceNioClientManager iceClientManager;

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
//    @Transactional
    public Long baseEdit(IceBase base) {
        timeHandle(base);
        base.setDebug(base.getDebug() == null ? 0 : base.getDebug());
        base.setScenes(base.getScenes() == null ? "" : base.getScenes());
        base.setStatus(base.getStatus() == null ? 1 : base.getStatus());
        base.setTimeType(base.getTimeType() == null ? 1 : base.getTimeType());
        base.setPriority(1L);
        IceTransferDto transferDto = new IceTransferDto();
        if (base.getId() == null) {
            /*for the new base, you need to create a new root in conf. the default root is none*/
            if (base.getConfId() == null) {
                IceConf createConf = new IceConf();
                createConf.setApp(base.getApp());
                createConf.setStatus(StatusEnum.ONLINE.getStatus());
                createConf.setType(NodeTypeEnum.NONE.getType());
                createConf.setInverse((byte) 0);
                createConf.setDebug((byte) 1);
                createConf.setUpdateAt(new Date());
                createConf.setTimeType(TimeTypeEnum.NONE.getType());
                iceConfMapper.insertSelective(createConf);
                iceServerService.updateLocalConfActiveCache(createConf);
                transferDto.setInsertOrUpdateConfs(Collections.singletonList(Constant.confToDto(createConf)));
                base.setConfId(createConf.getMixId());
            }
            base.setConfId(base.getConfId());
            iceBaseMapper.insertSelective(base);
            iceServerService.updateLocalBaseActiveCache(base);
            transferDto.setInsertOrUpdateBases(Collections.singletonList(Constant.baseToDto(base)));
        } else {
            IceBase origin = iceBaseMapper.selectByPrimaryKey(base.getId());
            if (origin == null || origin.getStatus().equals(StatusEnum.OFFLINE.getStatus())) {
                throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "iceId", base.getId());
            }
            base.setConfId(origin.getConfId());
            iceBaseMapper.updateByPrimaryKey(base);
            iceServerService.updateLocalBaseActiveCache(base);
            transferDto.setInsertOrUpdateBases(Collections.singletonList(Constant.baseToDto(base)));
        }
        transferDto.setVersion(iceServerService.getVersion());
        iceNioClientManager.release(base.getApp(), transferDto);
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
        if (base == null || !base.getApp().equals(app)) {
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
        return JacksonUtils.toJsonString(getPushData(base));
    }

    private PushData getPushData(IceBase base) {
        PushData pushData = new PushData();
        pushData.setApp(base.getApp());
        pushData.setBase(Constant.baseToDtoWithName(base));
        Collection<IceConf> confUpdates = iceServerService.getAllUpdateConfList(base.getApp(), base.getId());
        if (!CollectionUtils.isEmpty(confUpdates)) {
            pushData.setConfUpdates(Constant.confListToDtoListWithName(confUpdates));
        }
        Set<IceConf> activeConfs = iceServerService.getAllActiveConfSet(base.getApp(), base.getConfId());
        if (!CollectionUtils.isEmpty(activeConfs)) {
            pushData.setConfs(Constant.confListToDtoListWithName(activeConfs));
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
    public void rollback(Long pushId) throws JsonProcessingException {
        IcePushHistory history = pushHistoryMapper.selectByPrimaryKey(pushId);
        if (history == null) {
            throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "pushId", pushId);
        }
        importData(JacksonUtils.readJson(history.getPushData(), PushData.class));
    }

    @Override
//    @Transactional
    public void importData(PushData data) {
        Collection<IceConf> confUpdates = Constant.dtoListToConfList(data.getConfUpdates(), data.getApp());
        if (!CollectionUtils.isEmpty(confUpdates)) {
            for (IceConf conf : confUpdates) {
                IceConf oldConf = iceConfUpdateMapper.selectByPrimaryKey(conf.getId());
                conf.setUpdateAt(new Date());
                if (oldConf == null) {
                    iceConfUpdateMapper.insertWithId(conf);
                } else {
                    iceConfUpdateMapper.updateByPrimaryKey(conf);
                }
            }
        }
        Collection<IceConf> confs = Constant.dtoListToConfList(data.getConfs(), data.getApp());
        if (!CollectionUtils.isEmpty(confs)) {
            for (IceConf conf : confs) {
                IceConf oldConf = iceConfMapper.selectByPrimaryKey(conf.getMixId());
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
        // todo compare and send change
        IceTransferDto transferDto = new IceTransferDto();
        if (!CollectionUtils.isEmpty(confUpdates)) {
            iceServerService.updateLocalConfUpdateCaches(confUpdates);
        }
        if (!CollectionUtils.isEmpty(confs)) {
            iceServerService.updateLocalConfActiveCaches(confs);
            transferDto.setInsertOrUpdateConfs(Constant.confListToDtoList(confs));
        }
        if (base != null) {
            iceServerService.updateLocalBaseActiveCache(base);
            transferDto.setInsertOrUpdateBases(Collections.singletonList(Constant.baseToDto(base)));
        }
        iceClientManager.release(data.getApp(), transferDto);
    }

    @Override
    public void delete(Long pushId) {
        pushHistoryMapper.deleteByPrimaryKey(pushId);
    }
}

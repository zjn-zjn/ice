package com.ice.server.service.impl;

import com.ice.server.dao.mapper.IceConfMapper;
import com.ice.server.dao.model.IceConf;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceLeafClass;
import com.ice.server.service.IceConfService;
import com.ice.server.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class IceConfServiceImpl implements IceConfService {

    @Resource
    private IceConfMapper iceConfMapper;

    @Resource
    private ServerService serverService;

    @Override
    @Transactional
    public Long confEdit(IceConf conf, Long parentId, Long nextId) {
        conf.setUpdateAt(new Date());
        if (conf.getId() == null && (parentId == null || nextId == null)) {
            throw new ErrorCodeException(ErrorCode.NOT_NULL, "parentId or nextId");
        }
        if (conf.getId() == null) {
            if (parentId != null) {
                /*add son*/
                IceConf parent = iceConfMapper.selectByPrimaryKey(parentId);
                if (parent == null) {
                    throw new ErrorCodeException(ErrorCode.ID_NOT_EXIST, "parentId", parentId);
                }
                iceConfMapper.insertSelective(conf);
                Long id = conf.getId();
                if (StringUtils.isEmpty(parent.getSonIds())) {
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
        iceConfMapper.updateByPrimaryKeySelective(conf);
        return conf.getId();
    }

    @Override
    public List<IceLeafClass> getConfLeafClass(Integer app, Byte type) {
        List<IceLeafClass> list = new ArrayList<>();
        Map<String, Integer> leafClassMap = serverService.getLeafClassMap(app, type);
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
}

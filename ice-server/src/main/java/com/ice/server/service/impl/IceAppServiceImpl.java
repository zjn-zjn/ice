package com.ice.server.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ice.server.dao.mapper.IceAppMapper;
import com.ice.server.dao.model.IceApp;
import com.ice.server.dao.model.IceAppExample;
import com.ice.server.model.PageResult;
import com.ice.server.service.IceAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@Service
public class IceAppServiceImpl implements IceAppService {

    @Resource
    private IceAppMapper iceAppMapper;

    @Override
    public PageResult<IceApp> appList(Integer pageNum, Integer pageSize, String name, Integer app) {
        Page<IceApp> page = PageMethod.startPage(pageNum, pageSize);
        IceAppExample example = new IceAppExample();
        IceAppExample.Criteria criteria = example.createCriteria();
        criteria.andStatusEqualTo(true);
        if (app != null) {
            criteria.andIdEqualTo(app.longValue());
        }
        if (StringUtils.hasLength(name)) {
            criteria.andNameLike(name + "%");
        }
        iceAppMapper.selectByExample(example);
        PageResult<IceApp> pageResult = new PageResult<>();
        pageResult.setList(page.getResult());
        pageResult.setTotal(page.getTotal());
        pageResult.setPages(page.getPages());
        pageResult.setPageNum(page.getPageNum());
        pageResult.setPageSize(page.getPageSize());
        return pageResult;
    }

    @Override
    public Long appEdit(IceApp app) {
        app.setUpdateAt(new Date());
        if (app.getId() == null) {
            /*add*/
            app.setStatus(true);
            iceAppMapper.insertSelective(app);
            return app.getId();
        }
        iceAppMapper.updateByPrimaryKeySelective(app);
        return app.getId();
    }
}

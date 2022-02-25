package com.ice.server.controller;

import com.ice.common.enums.NodeTypeEnum;
import com.ice.server.dao.model.IceApp;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceAppService;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceEditService;
import com.ice.server.service.IceServerService;
import com.ice.server.rmi.IceRmiClientManager;
import org.jetbrains.annotations.Contract;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author zjn
 */

@RestController
@Deprecated
public class IceShowController {

    private final IceServerService iceServerService;

    @Resource
    private IceAppService iceAppService;

    @Resource
    private IceEditService editService;

    @Resource
    private IceBaseService baseService;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Contract(pure = true)
    public IceShowController(IceServerService iceServerService) {
        this.iceServerService = iceServerService;
    }

    @RequestMapping(value = "/ice/app/list", method = RequestMethod.GET)
    public WebResult<List<IceApp>> getIceApp(@RequestParam(defaultValue = "1") Integer pageIndex,
                                             @RequestParam(defaultValue = "1000") Integer pageSize) {
        WebResult<List<IceApp>> result = new WebResult<>();
        PageResult<IceApp> pageResult = iceAppService.appList(pageIndex, pageSize, null, null);
        if (pageResult == null) {
            result.setData(Collections.emptyList());
        } else {
            result.setData(pageResult.getList());
        }
        return result;
    }

    @RequestMapping(value = "/ice/conf/list", method = RequestMethod.GET)
    public WebResult<PageResult<IceBase>> getIceConfList(@RequestParam Integer app,
                                                         @RequestParam(defaultValue = "1") Integer pageId,
                                                         @RequestParam(defaultValue = "100") Integer pageSize,
                                                         @RequestParam(defaultValue = "") Long id,
                                                         @RequestParam(defaultValue = "") String scene,
                                                         @RequestParam(defaultValue = "") String name) {
        return new WebResult<>(baseService.baseList(new IceBaseSearch(app, id, name, scene, pageId, pageSize)));
    }
}

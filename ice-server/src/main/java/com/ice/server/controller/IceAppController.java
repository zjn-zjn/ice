package com.ice.server.controller;

import com.ice.server.dao.model.IceApp;
import com.ice.server.model.PageResult;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceAppService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * app crud
 *
 * @author zjn
 */
@CrossOrigin
@RestController
public class IceAppController {
    @Resource
    private IceAppService iceAppService;

    @RequestMapping(value = "/ice-server/app/list", method = RequestMethod.GET)
    public WebResult<PageResult<IceApp>> appList(@RequestParam(defaultValue = "1") Integer pageNum,
                                                 @RequestParam(defaultValue = "20") Integer pageSize,
                                                 @RequestParam(defaultValue = "") String name,
                                                 @RequestParam(required = false) Integer app) {
        WebResult<PageResult<IceApp>> result = new WebResult<>();
        result.setData(iceAppService.appList(pageNum, pageSize, name, app));
        return result;
    }

    @RequestMapping(value = "/ice-server/app/edit", method = RequestMethod.POST)
    public WebResult<Long> appEdit(@RequestBody IceApp app) {
        WebResult<Long> result = new WebResult<>();
        result.setData(iceAppService.appEdit(app));
        return result;
    }
}

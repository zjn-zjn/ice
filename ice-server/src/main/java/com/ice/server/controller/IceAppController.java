package com.ice.server.controller;

import com.ice.server.dao.model.IceApp;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.PageResult;
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
    public PageResult<IceApp> appList(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "20") Integer pageSize,
                                      @RequestParam(defaultValue = "") String name,
                                      @RequestParam(required = false) Integer app) {
        return iceAppService.appList(pageNum, pageSize, name, app);
    }

    @RequestMapping(value = "/ice-server/app/edit", method = RequestMethod.POST)
    public Long appEdit(@RequestBody IceApp app) {
        if (app == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR);
        }
        return iceAppService.appEdit(app);
    }
}

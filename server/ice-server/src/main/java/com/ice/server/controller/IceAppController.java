package com.ice.server.controller;

import com.ice.server.model.IceApp;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.PageResult;
import com.ice.server.service.IceAppService;
import com.ice.server.service.IceServerService;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * app crud
 *
 * @author waitmoon
 */
@CrossOrigin
@RestController
public class IceAppController {
    @Autowired
    private IceAppService iceAppService;

    @Autowired
    private IceServerService iceServerService;

    @RequestMapping(value = "/ice-server/app/list", method = RequestMethod.GET)
    public PageResult<IceApp> appList(@RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "1000") Integer pageSize,
                                      @RequestParam(defaultValue = "") String name,
                                      @RequestParam(required = false) Integer app) {
        return iceAppService.appList(pageNum, pageSize, name, app);
    }

    @RequestMapping(value = "/ice-server/app/edit", method = RequestMethod.POST)
    public Integer appEdit(@RequestBody IceApp app) {
        if (app == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app");
        }
        return iceAppService.appEdit(app);
    }

    @RequestMapping(value = "/ice-server/app/recycle", method = RequestMethod.GET)
    public void recycle(@RequestParam(required = false) Integer app) {
        iceServerService.recycle(app);
    }
}

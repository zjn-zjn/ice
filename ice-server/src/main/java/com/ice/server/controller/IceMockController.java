package com.ice.server.controller;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.server.model.WebResult;
import com.ice.server.rmi.IceRmiClientManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zjn
 */

@RestController
public class IceMockController {

    @Resource
    private IceRmiClientManager rmiClientManager;

    @RequestMapping(value = "/ice/rmi/mock", method = RequestMethod.POST)
    public WebResult<List<IceContext>> rmiMock(@RequestParam Integer app, @RequestBody IcePack pack) {
        if (app <= 0 || pack == null) {
            return new WebResult<>(-1, "参数不正确", null);
        }
        if (pack.getIceId() <= 0 && !StringUtils.hasLength(pack.getScene()) && pack.getConfId() <= 0) {
            return new WebResult<>(-1, "IceId,Scene和ConfId不能同时为空", null);
        }
        return WebResult.success(rmiClientManager.mock(app, pack));
    }

    @RequestMapping(value = "/ice/rmi/mocks", method = RequestMethod.POST)
    public WebResult<List<IceContext>> rmiMocks(@RequestParam Integer app, @RequestBody List<IcePack> packs) {
        if (app <= 0 || CollectionUtils.isEmpty(packs)) {
            return new WebResult<>(-1, "参数不正确", null);
        }
        List<IceContext> result = new ArrayList<>();
        for (IcePack pack : packs) {
            result.addAll(rmiClientManager.mock(app, pack));
        }
        return WebResult.success(result);
    }
}

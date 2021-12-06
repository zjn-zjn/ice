package com.ice.server.controller;

import com.github.kevinsawicki.http.HttpRequest;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IcePushHistory;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceServerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * base crud
 *
 * @author zjn
 */
@CrossOrigin
@RestController
public class IceBaseController {
    @Resource
    private IceBaseService iceBaseService;

    @Resource
    private IceServerService serverService;

    @Value("${environment:dev}")
    private String environment;

    @Value("${product.import.url:}")
    private String productImportUrl;

    @RequestMapping(value = "/ice-server/base/list", method = RequestMethod.POST)
    public WebResult<PageResult<IceBase>> baseList(@RequestBody IceBaseSearch search) {
        WebResult<PageResult<IceBase>> result = new WebResult<>();
        result.setData(iceBaseService.baseList(search));
        return result;
    }

    @RequestMapping(value = "/ice-server/base/edit", method = RequestMethod.POST)
    public WebResult<Long> baseEdit(@RequestBody IceBase base) {
        WebResult<Long> result = new WebResult<>();
        if (base == null) {
            result.setRet(-1);
            result.setMsg("base null");
            return result;
        }
        result.setData(iceBaseService.baseEdit(base));
        serverService.updateByEdit();
        return result;
    }

    @RequestMapping(value = "/ice-server/base/push", method = RequestMethod.GET)
    public WebResult<Long> basePush(@RequestParam Integer app, @RequestParam Long iceId, @RequestParam(required = false) String reason) {
        WebResult<Long> result = new WebResult<>();
        result.setData(iceBaseService.push(app, iceId, reason));
        return result;
    }

    @RequestMapping(value = "/ice-server/base/pro", method = RequestMethod.POST)
    public WebResult<Void> pro(@RequestBody String json) {
        WebResult<Void> result = new WebResult<>();
        if (!"product".equals(environment)) {
            int code = HttpRequest.post(productImportUrl)
                    .connectTimeout(5000)
                    .readTimeout(5000)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .send(json)
                    .code();
            result.setMsg(String.valueOf(code));
        }
        return result;
    }

    @RequestMapping(value = "/ice-server/base/push/history", method = RequestMethod.GET)
    public WebResult<PageResult<IcePushHistory>> history(@RequestParam Integer app,
                                                         @RequestParam Long iceId,
                                                         @RequestParam(defaultValue = "1") Integer pageNum,
                                                         @RequestParam(defaultValue = "20") Integer pageSize) {
        WebResult<PageResult<IcePushHistory>> result = new WebResult<>();
        result.setData(iceBaseService.history(app, iceId, pageNum, pageSize));
        return result;
    }

    @RequestMapping(value = "/ice-server/base/export", method = RequestMethod.GET)
    public WebResult<String> export(@RequestParam Long iceId,
                                    @RequestParam(required = false) Long pushId) {
        WebResult<String> result = new WebResult<>();
        result.setData(iceBaseService.exportData(iceId, pushId));
        return result;
    }

    @RequestMapping(value = "/ice-server/base/rollback", method = RequestMethod.GET)
    public WebResult<Void> rollback(@RequestParam Long pushId) {
        WebResult<Void> result = new WebResult<>();
        iceBaseService.rollback(pushId);
        serverService.updateByEdit();
        return result;
    }

    @RequestMapping(value = "/ice-server/base/import", method = RequestMethod.POST)
    public WebResult<Void> importData(@RequestBody PushData data) {
        WebResult<Void> result = new WebResult<>();
        iceBaseService.importData(data);
        serverService.updateByEdit();
        return result;
    }
}

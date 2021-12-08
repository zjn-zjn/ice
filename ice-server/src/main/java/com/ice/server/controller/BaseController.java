package com.ice.server.controller;

import com.github.kevinsawicki.http.HttpRequest;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IcePushHistory;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;
import com.ice.server.model.WebResult;
import com.ice.server.service.BaseService;
import com.ice.server.service.ServerService;
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
public class BaseController {
    @Resource
    private BaseService baseService;

    @Resource
    private ServerService serverService;

    @Value("${environment:dev}")
    private String environment;

    @Value("${product.import.url:}")
    private String productImportUrl;

    @RequestMapping(value = "/ice-server/base/list", method = RequestMethod.POST)
    public PageResult<IceBase> baseList(@RequestBody IceBaseSearch search) {
        return baseService.baseList(search);
    }

    @RequestMapping(value = "/ice-server/base/edit", method = RequestMethod.POST)
    public Long baseEdit(@RequestBody IceBase base) {
        if (base == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR);
        }
        Long id = baseService.baseEdit(base);
        serverService.updateByEdit();
        return id;
    }

    @RequestMapping(value = "/ice-server/base/push", method = RequestMethod.GET)
    public Long basePush(@RequestParam Integer app, @RequestParam Long iceId, @RequestParam(required = false) String reason) {
        return baseService.push(app, iceId, reason);
    }

    @RequestMapping(value = "/ice-server/base/pro", method = RequestMethod.POST)
    public int pro(@RequestBody String json) {
        if (!"product".equals(environment)) {
            return HttpRequest.post(productImportUrl)
                    .connectTimeout(5000)
                    .readTimeout(5000)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .send(json)
                    .code();
        }
        return 0;
    }

    @RequestMapping(value = "/ice-server/base/push/history", method = RequestMethod.GET)
    public PageResult<IcePushHistory> history(@RequestParam Integer app,
                                              @RequestParam Long iceId,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "20") Integer pageSize) {
        return baseService.history(app, iceId, pageNum, pageSize);
    }

    @RequestMapping(value = "/ice-server/base/export", method = RequestMethod.GET)
    public WebResult<String> export(@RequestParam Long iceId,
                         @RequestParam(required = false) Long pushId) {
        return WebResult.success(baseService.exportData(iceId, pushId));
    }

    @RequestMapping(value = "/ice-server/base/rollback", method = RequestMethod.GET)
    public WebResult<Void> rollback(@RequestParam Long pushId) {
        baseService.rollback(pushId);
        serverService.updateByEdit();
        return WebResult.success();
    }

    @RequestMapping(value = "/ice-server/base/import", method = RequestMethod.POST)
    public WebResult<Void> importData(@RequestBody PushData data) {
        baseService.importData(data);
        serverService.updateByEdit();
        return WebResult.success();
    }
}

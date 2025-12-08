package com.ice.server.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.core.utils.JacksonUtils;
import com.ice.server.model.IceBase;
import com.ice.server.model.IcePushHistory;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.PushData;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceServerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

/**
 * base crud
 *
 * @author waitmoon
 */
@CrossOrigin
@RestController
public class IceBaseController {
    @Autowired
    private IceBaseService iceBaseService;

    @Autowired
    private IceServerService iceServerService;

    @Value("${environment:dev}")
    private String environment;

    @Value("${product.import.url:}")
    private String productImportUrl;

    @RequestMapping(value = "/ice-server/base/list", method = RequestMethod.GET)
    public PageResult<IceBase> baseList(@RequestParam Integer app,
                                        @RequestParam(defaultValue = "1") Integer pageId,
                                        @RequestParam(defaultValue = "100") Integer pageSize,
                                        @RequestParam(defaultValue = "") Long id,
                                        @RequestParam(defaultValue = "") String scene,
                                        @RequestParam(defaultValue = "") String name) {
        return iceBaseService.baseList(new IceBaseSearch(app, id, name, scene, pageId, pageSize));
    }

    @RequestMapping(value = "/ice-server/base/edit", method = RequestMethod.POST)
    public Long baseEdit(@RequestBody IceBase base) {
        if (base == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "base");
        }
        return iceBaseService.baseEdit(base);
    }

    @RequestMapping(value = "/ice-server/base/backup", method = RequestMethod.GET)
    public Long basePush(@RequestParam Integer app, @RequestParam Long iceId, @RequestParam(required = false) String reason) {
        return iceBaseService.push(app, iceId, reason);
    }

//    @RequestMapping(value = "/ice-server/base/pro", method = RequestMethod.POST)
//    public int pro(@RequestBody String json) {
//        if (!"product".equals(environment)) {
//            return HttpRequest.post(productImportUrl)
//                    .connectTimeout(5000)
//                    .readTimeout(5000)
//                    .header("Content-Type", "application/json; charset=utf-8")
//                    .send(json)
//                    .code();
//        }
//        return 0;
//    }

    @RequestMapping(value = "/ice-server/base/backup/history", method = RequestMethod.GET)
    public PageResult<IcePushHistory> history(@RequestParam Integer app,
                                              @RequestParam Long iceId,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "1000") Integer pageSize) {
        return iceBaseService.history(app, iceId, pageNum, pageSize);
    }

    @RequestMapping(value = "/ice-server/base/backup/delete", method = RequestMethod.GET)
    public void delete(@RequestParam Integer app, @RequestParam Long pushId) {
        iceBaseService.delete(app, pushId);
    }

    @RequestMapping(value = "/ice-server/base/export", method = RequestMethod.GET)
    public WebResult<String> export(@RequestParam Integer app,
                                    @RequestParam Long iceId,
                                    @RequestParam(required = false) Long pushId) {
        return WebResult.success(iceBaseService.exportData(app, iceId, pushId));
    }

    @RequestMapping(value = "/ice-server/base/rollback", method = RequestMethod.GET)
    public void rollback(@RequestParam Integer app, @RequestParam Long pushId) throws JsonProcessingException {
        iceBaseService.rollback(app, pushId);
    }

    @RequestMapping(value = "/ice-server/base/import", method = RequestMethod.POST)
    public void importData(@RequestBody Map<String, String> map) throws JsonProcessingException {
        iceBaseService.importData(JacksonUtils.readJson(map.get("json"), PushData.class));
    }
}

package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.ice.server.model.IceBaseVo;
import com.ice.server.model.IceConfVo;
import com.ice.server.model.PushData;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceBaseService;
import com.ice.server.service.IceEditService;
import com.ice.server.service.IceServerService;
import com.github.kevinsawicki.http.HttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * @author zjn
 */

@RestController
@Deprecated
public class IceEditController {

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private IceEditService editService;

    @Resource
    private IceBaseService iceBaseService;

    @Resource
    private IceServerService iceServerService;

    @Value("${environment:dev}")
    private String environment;

    @Value("${product.import.url:}")
    private String productImportUrl;

    /*
     * 编辑ice
     */
    @Deprecated
    @RequestMapping(value = "/ice/edit", method = RequestMethod.POST)
    public WebResult editBase(@RequestBody IceBaseVo baseVo) {
        baseVo.setStatus((byte) 1);
        WebResult result = editService.editBase(baseVo);
        iceServerService.updateByEdit();
        return result;
    }

    /*
     * 编辑节点
     */
    @Deprecated
    @RequestMapping(value = "/ice/conf/edit", method = RequestMethod.POST)
    public WebResult editConf(@RequestBody IceConfVo confVo) {
        WebResult result = editService.editConf(confVo.getApp(), confVo.getType(), confVo.getIceId(), confVo);
        iceServerService.updateByEdit();
        return result;
    }

    /*
     * 获取叶子节点类
     */
    @Deprecated
    @RequestMapping(value = "/ice/conf/edit/getClass", method = RequestMethod.GET)
    public WebResult getClass(@RequestParam Integer app, @RequestParam Byte type) {
        return editService.getLeafClass(app, type);
    }

    /*
     * 发布
     */
    @Deprecated
    @RequestMapping(value = "/ice/conf/push", method = RequestMethod.POST)
    public WebResult push(@RequestBody Map map) {
        return WebResult.success(iceBaseService.push((Integer) map.get("app"), Long.parseLong(map.get("iceId").toString()), (String) map.get("reason")));
    }

    /*
     * 发布到线上
     */
    @Deprecated
    @RequestMapping(value = "/ice/topro", method = RequestMethod.POST)
    public WebResult toPro(@RequestBody Map map) {
        WebResult result = new WebResult();
        if (!"product".equals(environment)) {
            int code = HttpRequest.post(productImportUrl)
                    .connectTimeout(5000)
                    .readTimeout(5000)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .send(JSON.toJSONString(map))
                    .code();
            result.setMsg(String.valueOf(code));
        }
        return result;
    }

    /*
     * 发布历史
     */
    @Deprecated
    @RequestMapping(value = "/ice/conf/push/history", method = RequestMethod.GET)
    public WebResult history(@RequestParam Integer app,
                             @RequestParam Long iceId) {
        return editService.history(app, iceId);
    }

    /*
     * 导出
     */
    @RequestMapping(value = "/ice/conf/export", method = RequestMethod.GET)
    public WebResult exportData(@RequestParam Long iceId,
                                @RequestParam(defaultValue = "-1") Long pushId) {
        return WebResult.success(iceBaseService.exportData(iceId, pushId));
    }

    /*
     * 回滚
     */
    @RequestMapping(value = "/ice/conf/rollback", method = RequestMethod.GET)
    public WebResult exportData(@RequestParam Long pushId) {
        iceBaseService.rollback(pushId);
        iceServerService.updateByEdit();
        return WebResult.success();
    }

    /*
     * 导入
     */
    @RequestMapping(value = "/ice/conf/import", method = RequestMethod.POST)
    public WebResult importData(@RequestBody Map map) {
        iceBaseService.importData(JSON.parseObject((String) map.get("data"), PushData.class));
        iceServerService.updateByEdit();
        return WebResult.success();
    }
}

package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.ice.server.model.IceBaseVo;
import com.ice.server.model.IceConfVo;
import com.ice.server.model.WebResult;
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
public class IceEditController {

  private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  @Resource
  private IceEditService editService;

  @Resource
  private IceServerService serverService;

  @Value("${environment.id}")
  private String environmentId;

  /**
   * 编辑ice
   */
  @RequestMapping(value = "/ice/edit", method = RequestMethod.POST)
  public WebResult editBase(@RequestBody IceBaseVo baseVo) {
    WebResult result = editService.editBase(baseVo);
    serverService.updateByEdit();
    return result;
  }

  /**
   * 编辑节点
   */
  @RequestMapping(value = "/ice/conf/edit", method = RequestMethod.POST)
  public WebResult editConf(@RequestBody IceConfVo confVo) {
    WebResult result = editService.editConf(confVo.getApp(), confVo.getType(), confVo.getIceId(), confVo);
    serverService.updateByEdit();
    return result;
  }

  /**
   * 获取叶子节点类
   */
  @RequestMapping(value = "/ice/conf/edit/getClass", method = RequestMethod.GET)
  public WebResult getClass(@RequestParam Integer app, @RequestParam Byte type) {
    return editService.getLeafClass(app, type);
  }

  /**
   * 发布
   */
  @RequestMapping(value = "/ice/conf/push", method = RequestMethod.POST)
  public WebResult push(@RequestBody Map map) {
    return editService.push((Integer) map.get("app"), Long.parseLong(map.get("iceId").toString()), (String) map.get("reason"));
  }

  /**
   * 发布到线上
   */
  @RequestMapping(value = "/ice/topro", method = RequestMethod.POST)
  public WebResult toPro(@RequestBody Map map) {
    WebResult result = new WebResult();
    if (!"1".equals(environmentId)) {
      int code = HttpRequest.post("http://127.0.0.1/ice-server/ice/conf/import")
          .connectTimeout(5000)
          .readTimeout(5000)
          .header("Content-Type", "application/json; charset=utf-8")
          .send(JSON.toJSONString(map))
          .code();
      result.setMsg(String.valueOf(code));
    }
    return result;
  }

  /**
   * 发布历史
   */
  @RequestMapping(value = "/ice/conf/push/history", method = RequestMethod.GET)
  public WebResult history(@RequestParam Integer app,
                           @RequestParam Long iceId) {
    return editService.history(app, iceId);
  }

  /**
   * 导出
   */
  @RequestMapping(value = "/ice/conf/export", method = RequestMethod.GET)
  public WebResult exportData(@RequestParam Long iceId,
                              @RequestParam(defaultValue = "-1") Long pushId) {
    return editService.exportData(iceId, pushId);
  }

  /**
   * 回滚
   */
  @RequestMapping(value = "/ice/conf/rollback", method = RequestMethod.GET)
  public WebResult exportData(@RequestParam Long pushId) {
    WebResult result = editService.rollback(pushId);
    serverService.updateByEdit();
    return result;
  }

  /**
   * 导入
   */
  @RequestMapping(value = "/ice/conf/import", method = RequestMethod.POST)
  public WebResult importData(@RequestBody Map map) {
    WebResult result = editService.importData((String) map.get("data"));
    serverService.updateByEdit();
    return result;
  }

//  /**
//   * 复制
//   */
//  @RequestMapping(value = "/ice/conf/copy", method = RequestMethod.POST)
//  public WebResult copyData(@RequestBody String data) {
//    WebResult result = null;
//    if (!environmentId.equals("1")) {
//      result = editService.copyData(data);
//      serverService.updateByEdit();
//    }
//    return result;
//  }
}

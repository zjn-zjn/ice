package com.ice.test.controller;

import com.alibaba.fastjson.JSON;
import com.ice.client.IceClient;
import com.ice.core.context.IcePack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author zjn
 */
@Slf4j
@RestController
public class TestController {

  @RequestMapping(value = "/test", method = RequestMethod.POST)
  public String test(@RequestBody Map map) {
    IcePack pack = JSON.parseObject(JSON.toJSONString(map), IcePack.class);
    return JSON.toJSONString(IceClient.processCxt(pack));
  }
}

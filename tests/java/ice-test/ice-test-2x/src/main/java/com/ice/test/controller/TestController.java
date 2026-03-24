package com.ice.test.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.core.Ice;
import com.ice.core.context.IceRoam;
import com.ice.core.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author waitmoon
 */
@Slf4j
@RestController
public class TestController {

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public String test(@RequestBody Map<String, Object> map) throws JsonProcessingException {
        IceRoam roam = JacksonUtils.readJson(JacksonUtils.toJsonString(map), IceRoam.class);
        return JacksonUtils.toJsonString(Ice.syncProcess(roam));
    }

    @RequestMapping(value = "/recharge", method = RequestMethod.GET)
    public String recharge(@RequestParam Integer cost, @RequestParam Integer uid) {
        IceRoam roam = IceRoam.create();
        roam.setScene("recharge");
        roam.put("cost", cost);
        roam.put("uid", uid);
        Ice.syncProcess(roam);
        return JacksonUtils.toJsonString(roam);
    }

    @RequestMapping(value = "/consume", method = RequestMethod.GET)
    public String consume(@RequestParam Integer cost, @RequestParam Integer uid) {
        IceRoam roam = IceRoam.create();
        roam.setScene("consume");
        roam.put("cost", cost);
        roam.put("uid", uid);
        Ice.syncProcess(roam);
        return JacksonUtils.toJsonString(roam.get("result"));
    }
}

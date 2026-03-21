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
@RequestMapping("/")
public class TestController {

    @PostMapping("/test")
    public String test(@RequestBody Map<String, Object> map) throws JsonProcessingException {
        IceRoam roam = JacksonUtils.readJson(JacksonUtils.toJsonString(map), IceRoam.class);
        return JacksonUtils.toJsonString(Ice.syncProcess(roam));
    }

    @GetMapping("/recharge")
    public String recharge(@RequestParam Integer cost, @RequestParam Integer uid) {
        IceRoam roam = IceRoam.create();
        roam.getIceMeta().setScene("recharge");
        roam.put("cost", cost);
        roam.put("uid", uid);
        Ice.syncProcess(roam);
        return JacksonUtils.toJsonString(roam);
    }

    @GetMapping("/consume")
    public String consume(@RequestParam Integer cost, @RequestParam Integer uid) {
        IceRoam roam = IceRoam.create();
        roam.getIceMeta().setScene("consume");
        roam.put("cost", cost);
        roam.put("uid", uid);
        Ice.syncProcess(roam);
        return JacksonUtils.toJsonString(roam.get("result"));
    }
}

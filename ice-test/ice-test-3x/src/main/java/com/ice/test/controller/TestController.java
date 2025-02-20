package com.ice.test.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.core.Ice;
import com.ice.core.context.IcePack;
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
        IcePack pack = JacksonUtils.readJson(JacksonUtils.toJsonString(map), IcePack.class);
        return JacksonUtils.toJsonString(Ice.processCtx(pack));
    }

    @GetMapping("/recharge")
    public String recharge(@RequestParam Integer cost, @RequestParam Integer uid) {
        IcePack pack = new IcePack();
        pack.setScene("recharge");
        IceRoam roam = new IceRoam();
        roam.put("cost", cost);
        roam.put("uid", uid);
        pack.setRoam(roam);
        Ice.syncProcess(pack);
        return JacksonUtils.toJsonString(roam.get("result"));
    }

    @GetMapping("/consume")
    public String consume(@RequestParam Integer cost, @RequestParam Integer uid) {
        IcePack pack = new IcePack();
        pack.setScene("consume");
        IceRoam roam = new IceRoam();
        roam.put("cost", cost);
        roam.put("uid", uid);
        pack.setRoam(roam);
        Ice.syncProcess(pack);
        return JacksonUtils.toJsonString(roam.get("result"));
    }
}

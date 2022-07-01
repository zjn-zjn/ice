package com.ice.test.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.ice.common.utils.JacksonUtils;
import com.ice.core.Ice;
import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
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
        IcePack pack = JacksonUtils.readJson(JacksonUtils.toJsonString(map), IcePack.class);
        return JacksonUtils.toJsonString(Ice.processCtx(pack));
    }

    @RequestMapping(value = "/recharge", method = RequestMethod.GET)
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

    @RequestMapping(value = "/consume", method = RequestMethod.GET)
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

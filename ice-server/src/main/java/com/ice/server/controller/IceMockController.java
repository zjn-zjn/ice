package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ice.core.context.IcePack;
import com.ice.server.constant.Constant;
import com.ice.server.model.WebResult;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zjn
 */

@RestController
public class IceMockController {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Resource
    private AmqpTemplate amqpTemplate;

    @RequestMapping(value = "/ice/amqp/mock", method = RequestMethod.POST)
    public WebResult<Void> amqpMock(@RequestParam Integer app, @RequestBody IcePack pack) {
        if (app <= 0 || pack == null) {
            return new WebResult<>(-1, "参数不正确", null);
        }
        if (pack.getIceId() <= 0 && StringUtils.isEmpty(pack.getScene()) && StringUtils.isEmpty(pack.getConfId())) {
            return new WebResult<>(-1, "IceId,Scene和ConfId不能同时为空", null);
        }
        amqpTemplate.convertAndSend(Constant.getMockExchange(), String.valueOf(app),
                JSON.toJSONString(pack, SerializerFeature.WriteClassName));
        return new WebResult<>();
    }

    @RequestMapping(value = "/ice/amqp/mocks", method = RequestMethod.POST)
    public WebResult<List<IcePack>> amqpMocks(@RequestParam Integer app, @RequestBody List<IcePack> packs) {
        if (app <= 0 || CollectionUtils.isEmpty(packs)) {
            return new WebResult<>(-1, "参数不正确", null);
        }
        WebResult<List<IcePack>> result = new WebResult<>();
        List<IcePack> errPacks = new ArrayList<>();
        for (IcePack pack : packs) {
            if (pack.getIceId() <= 0 && StringUtils.isEmpty(pack.getScene())) {
                errPacks.add(pack);
                continue;
            }
            amqpTemplate.convertAndSend(Constant.getMockExchange(), String.valueOf(app),
                    JSON.toJSONString(pack, SerializerFeature.WriteClassName));
        }
        result.setData(errPacks);
        return result;
    }
}

package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.ice.server.constant.Constant;
import com.ice.server.dao.model.IceConf;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceEditService;
import com.ice.server.service.IceServerService;
import org.jetbrains.annotations.Contract;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author zjn
 */

@RestController
@Deprecated
public class IceShowController {

    private final IceServerService iceServerService;

    @Resource
    private IceEditService editService;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AmqpTemplate amqpTemplate;

    @Contract(pure = true)
    public IceShowController(IceServerService iceServerService) {
        this.iceServerService = iceServerService;
    }

    @RequestMapping(value = "/ice/app/list", method = RequestMethod.GET)
    public WebResult<List<IceAppDto>> getIceApp(@RequestParam(defaultValue = "1") Integer pageIndex,
                                                @RequestParam(defaultValue = "100") Integer pageSize) {
        WebResult<List<IceAppDto>> result = new WebResult<>();
        result.setData(Collections.singletonList(new IceAppDto(1, "Test", "")));
        return result;
    }

    @RequestMapping(value = "/ice/conf/list", method = RequestMethod.GET)
    public WebResult getIceConf(@RequestParam Integer app, @RequestParam(defaultValue = "1") Integer pageIndex,
                                @RequestParam(defaultValue = "100") Integer pageSize) {
        return editService.getBase(app, pageIndex, pageSize);
    }

    @RequestMapping("/ice/conf/detail")
    public WebResult getIceAppConf(@RequestParam Integer app, @RequestParam Long iceId) {
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getShowConfExchange(), String.valueOf(app),
                String.valueOf(iceId));
        if (obj != null) {
            String json = (String) obj;
            if (!StringUtils.isEmpty(json)) {
                Map map = JSON.parseObject(json, Map.class);
                if (!CollectionUtils.isEmpty(map)) {
                    Map handlerMap = (Map) map.get("handler");
                    if (!CollectionUtils.isEmpty(handlerMap)) {
                        Map rootMap = (Map) handlerMap.get("root");
                        if (!CollectionUtils.isEmpty(rootMap)) {
                            Set<Long> nodeIdSet = new HashSet<>();
                            assemble(app, rootMap, nodeIdSet);
                            return new WebResult<>(map);
                        }
                    }
                }
            }
        }
        return new WebResult<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map> getChild(Map map) {
        return (List) map.get("children");
    }

    @SuppressWarnings("unchecked")
    private void assemble(Integer app, Map map, Set<Long> nodeIdSet) {
        if (map == null) {
            return;
        }
        Long nodeId = (Long) map.get("iceNodeId");
        if (nodeId == null) {
            return;
        }
        Map forward = (Map) map.get("iceForward");
        if (forward != null) {
            forward.put("nextId", nodeId);
        }
        assembleOther(app, map, nodeIdSet);
        assemble(app, forward, nodeIdSet);
        List<Map> children = getChild(map);
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (Map child : children) {
            child.put("parentId", nodeId);
            assemble(app, child, nodeIdSet);
        }
    }

    @SuppressWarnings("unchecked")
    private void assembleOther(Integer app, Map map, Set<Long> nodeIdSet) {
        Long nodeId = (Long) map.get("iceNodeId");
        if (nodeId == null /*|| nodeIdSet.contains(iceNodeId)*/) {
            return;
        }
        nodeIdSet.add(nodeId);
        Map showConf = new HashMap(map.size());
        Set<Map.Entry> entrySet = map.entrySet();
        List<Object> needRemoveKey = new ArrayList<>(map.size());
        for (Map.Entry entry : entrySet) {
            if (!("iceForward".equals(entry.getKey()) || "children".equals(entry.getKey()) || "nextId".equals(entry.getKey()) || "parentId".equals(entry.getKey()))) {
                needRemoveKey.add(entry.getKey());
                if (!adjust(entry.getKey(), entry.getValue(), showConf)) {
                    showConf.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Object iceForward = map.get("iceForward");
        if (iceForward != null) {
            map.remove("iceForward");
            map.put("forward", iceForward);
        }
        for (Object removeKey : needRemoveKey) {
            map.remove(removeKey);
        }
        map.put("showConf", showConf);
        IceConf iceConf = iceServerService.getActiveConfById(app, nodeId);
        if (iceConf != null) {
            if (!StringUtils.isEmpty(iceConf.getName())) {
                showConf.put("nodeName", iceConf.getName());
            }
            if (!StringUtils.isEmpty(iceConf.getConfField())) {
                showConf.put("confField", iceConf.getConfField());
            }
            if (!StringUtils.isEmpty(iceConf.getConfName())) {
                showConf.put("confName", iceConf.getId() + "-" + iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1));
            }
            if (iceConf.getType() != null) {
                showConf.put("nodeType", iceConf.getType());
            }
            if (iceConf.getStart() != null) {
                map.put("start", iceConf.getStart());
            }
            if (iceConf.getEnd() != null) {
                map.put("end", iceConf.getEnd());
            }
            if (iceConf.getTimeType() != null) {
                map.put("timeType", iceConf.getTimeType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private boolean adjust(Object key, Object value, Map showConf) {
        if ("iceNodeId".equals(key)) {
            showConf.put("nodeId", value);
            return true;
        }
        if ("iceNodeDebug".equals(key)) {
            showConf.put("debug", value);
            return true;
        }
        if ("iceStart".equals(key)) {
            long time = Long.parseLong(value.toString());
            if (time != 0) {
                showConf.put("开始时间", sdf.format(new Date(time)));
            }
            return true;
        }
        if ("iceEnd".equals(key)) {
            long time = Long.parseLong(value.toString());
            if (time != 0) {
                showConf.put("结束时间", sdf.format(new Date(time)));
            }
            return true;
        }
        if ("iceInverse".equals(key)) {
            if (Boolean.parseBoolean(value.toString())) {
                showConf.put("inverse", value);
            }
            return true;
        }
        return false;
    }
}

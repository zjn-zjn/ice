package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.ice.common.constant.Constant;
import com.ice.server.dao.model.IceConf;
import com.ice.server.model.IceLeafClass;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * conf crud
 * @author zjn
 */
@CrossOrigin
@RestController
public class IceConfController {
    @Resource
    private IceConfService iceConfService;

    @Resource
    private IceServerService serverService;

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AmqpTemplate amqpTemplate;

    @RequestMapping(value = "/ice-server/conf/edit", method = RequestMethod.POST)
    public WebResult<Long> confEdit(@RequestBody IceConf conf, @RequestParam(required = false) Long parentId, @RequestParam(required = false) Long nextId) {
        WebResult<Long> result = new WebResult<>();
        if (conf == null) {
            result.setRet(-1);
            result.setMsg("conf null");
            return result;
        }
        if (conf.getId() == null && parentId == null) {
            result.setRet(-1);
            result.setMsg("parentId can not be null in add");
            return result;
        }
        Long id = iceConfService.confEdit(conf, parentId, nextId);
        if (id <= 0) {
            result.setRet(-1);
            result.setMsg("error");
            return result;
        }
        result.setData(id);
        serverService.updateByEdit();
        return result;
    }

    @RequestMapping(value = "/ice-server/conf/leaf/class", method = RequestMethod.GET)
    public WebResult<List<IceLeafClass>> confLeafClass(@RequestParam Integer app, @RequestParam Byte type) {
        WebResult<List<IceLeafClass>> result = new WebResult<>();
        result.setData(iceConfService.confLeafClass(app, type));
        serverService.updateByEdit();
        return result;
    }

    @RequestMapping("/ice-server/conf/detail")
    @SuppressWarnings("unchecked")
    public WebResult<Map<String, Object>> confDetail(@RequestParam Integer app, @RequestParam Long confId) {
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getConfExchange(), String.valueOf(app),
                String.valueOf(confId));
        if (obj != null) {
            String json = (String) obj;
            if (!StringUtils.isEmpty(json)) {
                Map<String, Object> map = JSON.parseObject(json, new TypeReference<Map<String, Object>>() {
                });
                if (!CollectionUtils.isEmpty(map)) {
                    Map<String, Object> handlerMap = (Map<String, Object>) map.get("handler");
                    if (!CollectionUtils.isEmpty(handlerMap)) {
                        Map<String, Object> rootMap = (Map<String, Object>) handlerMap.get("root");
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
    private List<Map<String, Object>> getChild(Map<String, Object> map) {
        return (List<Map<String, Object>>) map.get("children");
    }

    @SuppressWarnings("unchecked")
    private void assemble(Integer app, Map<String, Object> map, Set<Long> nodeIdSet) {
        if (map == null) {
            return;
        }
        Long nodeId = (Long) map.get("iceNodeId");
        if (nodeId == null) {
            return;
        }
        Map<String, Object> forward = (Map<String, Object>) map.get("iceForward");
        if (forward != null) {
            forward.put("nextId", nodeId);
        }
        assembleOther(app, map, nodeIdSet);
        assemble(app, forward, nodeIdSet);
        List<Map<String, Object>> children = getChild(map);
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (Map<String, Object> child : children) {
            child.put("parentId", nodeId);
            assemble(app, child, nodeIdSet);
        }
    }

    private void assembleOther(Integer app, Map<String, Object> map, Set<Long> nodeIdSet) {
        Long nodeId = (Long) map.get("iceNodeId");
        if (nodeId == null /*|| nodeIdSet.contains(iceNodeId)*/) {
            return;
        }
        nodeIdSet.add(nodeId);
        Map<String, Object> showConf = new HashMap<>(map.size());
        Set<Map.Entry<String, Object>> entrySet = map.entrySet();
        List<String> needRemoveKey = new ArrayList<>(map.size());
        for (Map.Entry<String, Object> entry : entrySet) {
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
        for (String removeKey : needRemoveKey) {
            map.remove(removeKey);
        }
        map.put("showConf", showConf);
        IceConf iceConf = serverService.getActiveConfById(app, nodeId);
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

    private boolean adjust(Object key, Object value, Map<String, Object> showConf) {
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
                showConf.put("开始时间", SDF.format(new Date(time)));
            }
            return true;
        }
        if ("iceEnd".equals(key)) {
            long time = Long.parseLong(value.toString());
            if (time != 0) {
                showConf.put("结束时间", SDF.format(new Date(time)));
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

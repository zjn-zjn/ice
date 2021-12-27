package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.ice.common.enums.NodeTypeEnum;
import com.ice.server.constant.Constant;
import com.ice.server.dao.model.IceApp;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;
import com.ice.server.model.IceBaseSearch;
import com.ice.server.model.PageResult;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceAppService;
import com.ice.server.service.IceBaseService;
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
    private IceAppService iceAppService;

    @Resource
    private IceEditService editService;

    @Resource
    private IceBaseService baseService;

    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AmqpTemplate amqpTemplate;

    @Contract(pure = true)
    public IceShowController(IceServerService iceServerService) {
        this.iceServerService = iceServerService;
    }

    @RequestMapping(value = "/ice/app/list", method = RequestMethod.GET)
    public WebResult<List<IceApp>> getIceApp(@RequestParam(defaultValue = "1") Integer pageIndex,
                                             @RequestParam(defaultValue = "1000") Integer pageSize) {
        WebResult<List<IceApp>> result = new WebResult<>();
        PageResult<IceApp> pageResult = iceAppService.appList(pageIndex, pageSize, null, null);
        if (pageResult == null) {
            result.setData(Collections.emptyList());
        } else {
            result.setData(pageResult.getList());
        }
        return result;
    }

    @RequestMapping(value = "/ice/conf/list", method = RequestMethod.GET)
    public WebResult<PageResult<IceBase>> getIceConfList(@RequestParam Integer app,
                                                      @RequestParam(defaultValue = "1") Integer pageId,
                                                      @RequestParam(defaultValue = "100") Integer pageSize,
                                                      @RequestParam(defaultValue = "") Long id,
                                                      @RequestParam(defaultValue = "") String scene,
                                                      @RequestParam(defaultValue = "") String name) {
        return new WebResult<>(baseService.baseList(new IceBaseSearch(app, id, name, scene, pageId, pageSize)));
    }

    @RequestMapping("/ice/conf/detail")
    public WebResult getIceAppConf(@RequestParam Integer app, @RequestParam Long iceId) {
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getShowConfExchange(), String.valueOf(app),
                String.valueOf(iceId));
        if (obj != null) {
            String json = (String) obj;
            if (StringUtils.hasLength(json)) {
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
        return new WebResult<>(-1, "no available client");
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
        Long nodeId = (Long) map.get("nodeId");
        if (nodeId == null) {
            return;
        }
        Map forward = (Map) map.get("forward");
        if (forward != null) {
            forward.put("nextId", nodeId);
        }
        assembleOther(app, map, nodeIdSet);
        assemble(app, forward, nodeIdSet);
        List<Map> children = getChild(map);
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        int i = 0;
        for (Map child : children) {
            child.put("index", i);
            i++;
            child.put("parentId", nodeId);
            assemble(app, child, nodeIdSet);
        }
    }

    @SuppressWarnings("unchecked")
    private void assembleOther(Integer app, Map map, Set<Long> nodeIdSet) {
        Long nodeId = (Long) map.get("nodeId");
        if (nodeId == null /*|| nodeIdSet.contains(iceNodeId)*/) {
            return;
        }
        nodeIdSet.add(nodeId);
        Map showConf = new HashMap(map.size());
        Set<Map.Entry> entrySet = map.entrySet();
        List<Object> needRemoveKey = new ArrayList<>(map.size());
        for (Map.Entry entry : entrySet) {
            if (!("forward".equals(entry.getKey()) || "children".equals(entry.getKey()) || "nextId".equals(entry.getKey()) || "parentId".equals(entry.getKey()))) {
                needRemoveKey.add(entry.getKey());
                if (!adjust(entry.getKey(), entry.getValue(), showConf)) {
                    showConf.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Object removeKey : needRemoveKey) {
            map.remove(removeKey);
        }
        map.put("showConf", showConf);
        IceConf iceConf = iceServerService.getActiveConfById(app, nodeId);
        if (iceConf != null) {
            if (StringUtils.hasLength(iceConf.getName())) {
                showConf.put("nodeName", iceConf.getName());
                showConf.put("name", iceConf.getName());
            }
            if (StringUtils.hasLength(iceConf.getConfField())) {
                showConf.put("confField", iceConf.getConfField());
            }
            if (StringUtils.hasLength(iceConf.getConfName())) {
                showConf.put("confName", iceConf.getConfName());
            }
            if (iceConf.getType() != null) {
                showConf.put("nodeType", iceConf.getType());
                if (Constant.isRelation(iceConf.getType())) {
                    showConf.put("labelName", nodeId + "-" + NodeTypeEnum.getEnum(iceConf.getType()).name() + (StringUtils.hasLength(iceConf.getName()) ? ("-" + iceConf.getName()) : ""));
                } else {
                    showConf.put("labelName", nodeId + "-" + (StringUtils.hasLength(iceConf.getConfName()) ? iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1) : " ") + (StringUtils.hasLength(iceConf.getName()) ? ("-" + iceConf.getName()) : ""));
                }
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
        if ("nodeId".equals(key)) {
            showConf.put("nodeId", value);
            return true;
        }
        if ("debug".equals(key)) {
            showConf.put("debug", value);
            return true;
        }
        if ("inverse".equals(key)) {
            if (Boolean.parseBoolean(value.toString())) {
                showConf.put("inverse", value);
            }
            return true;
        }
        return false;
    }
}

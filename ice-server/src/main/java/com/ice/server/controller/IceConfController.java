package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.ice.common.constant.Constant;
import com.ice.common.model.IceClientConf;
import com.ice.common.model.IceClientHandler;
import com.ice.common.model.IceClientNode;
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
 *
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
        if (conf.getId() == null && (parentId == null || nextId == null)) {
            result.setRet(-1);
            result.setMsg("parentId can not be null in add");
            return result;
        }
        //conf node edit-delete just pick up from parent
        conf.setStatus((byte)1);
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

    @RequestMapping(value = "/ice-server/conf/detail", method = RequestMethod.GET)
    public WebResult<IceClientConf> confDetail(@RequestParam Integer app, @RequestParam Long iceId) {
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getConfExchange(), String.valueOf(app),
                String.valueOf(iceId));
        if (obj != null) {
            String json = (String) obj;
            if (!StringUtils.isEmpty(json)) {
                IceClientConf clientConf = JSON.parseObject(json, IceClientConf.class);
                if (clientConf != null) {
                    IceClientHandler handler = clientConf.getHandler();
                    if (handler != null) {
                        IceClientNode root = handler.getRoot();
                        if (root != null) {
                            assemble(app, root);
                            return new WebResult<>(clientConf);
                        }
                    }
                }
            }
        }
        return new WebResult<>();
    }

    private void assemble(Integer app, IceClientNode clientNode) {
        if (clientNode == null) {
            return;
        }
        Long nodeId = clientNode.getIceNodeId();
        IceClientNode forward = clientNode.getIceForward();
        if (forward != null) {
            forward.setNextId(nodeId);
        }
        assembleInfoInServer(app, clientNode);
        assemble(app, forward);
        List<IceClientNode> children = clientNode.getChildren();
        if (CollectionUtils.isEmpty(children)) {
            return;
        }
        for (IceClientNode child : children) {
            child.setParentId(nodeId);
            assemble(app, child);
        }
    }

    private void assembleInfoInServer(Integer app, IceClientNode clientNode) {
        Long nodeId = clientNode.getIceNodeId();
        IceConf iceConf = serverService.getActiveConfById(app, nodeId);
        if (iceConf != null) {
            if (!StringUtils.isEmpty(iceConf.getName())) {
                clientNode.setNodeName(iceConf.getName());
            }
            if (!StringUtils.isEmpty(iceConf.getConfField())) {
                clientNode.setConfField(iceConf.getConfField());
            }
            if (!StringUtils.isEmpty(iceConf.getConfName())) {
                clientNode.setConfName(iceConf.getId() + "-" + iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1));
            }
            if (iceConf.getType() != null) {
                clientNode.setNodeType(iceConf.getType());
            }
        }
    }
}

package com.ice.server.controller;

import com.alibaba.fastjson.JSON;
import com.ice.common.constant.Constant;
import com.ice.common.model.IceClientConf;
import com.ice.common.model.IceClientNode;
import com.ice.server.dao.model.IceConf;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceLeafClass;
import com.ice.server.service.IceConfService;
import com.ice.server.service.ServerService;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

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
    private ServerService serverService;

    @Resource
    private AmqpTemplate amqpTemplate;

    @RequestMapping(value = "/ice-server/conf/edit", method = RequestMethod.POST)
    public Long confEdit(@RequestBody IceConf conf, @RequestParam(required = false) Long parentId, @RequestParam(required = false) Long nextId) {
        //conf node edit-delete just pick up from parent
        conf.setStatus((byte) 1);
        Long id = iceConfService.confEdit(conf, parentId, nextId);
        serverService.updateByEdit();
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/leaf/class", method = RequestMethod.GET)
    public List<IceLeafClass> getConfLeafClass(@RequestParam Integer app, @RequestParam Byte type) {
        return iceConfService.getConfLeafClass(app, type);
    }

    @RequestMapping(value = "/ice-server/conf/detail", method = RequestMethod.GET)
    public IceClientConf confDetail(@RequestParam Integer app, @RequestParam Long confId) {
        Object obj = amqpTemplate.convertSendAndReceive(Constant.getConfExchange(), String.valueOf(app),
                String.valueOf(confId));
        if (obj == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, null);
        }
        String json = (String) obj;
        if (StringUtils.isEmpty(json)) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, null);
        }
        IceClientConf clientConf = JSON.parseObject(json, IceClientConf.class);
        IceClientNode node = clientConf.getNode();
        if (node == null) {
            throw new ErrorCodeException(ErrorCode.REMOTE_CONF_NOT_FOUND, app, "confId", confId, JSON.toJSONString(clientConf));
        }
        assemble(app, node);
        return clientConf;
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
                clientNode.setConfName(iceConf.getConfName().substring(iceConf.getConfName().lastIndexOf('.') + 1));
            }
            if (iceConf.getType() != null) {
                clientNode.setNodeType(iceConf.getType());
            }
        }
    }
}

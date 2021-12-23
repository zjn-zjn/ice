package com.ice.server.controller;

import com.ice.common.model.IceClientConf;
import com.ice.server.dao.model.IceConf;
import com.ice.server.model.IceLeafClass;
import com.ice.server.model.WebResult;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
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
    private IceServerService iceServerService;

    @RequestMapping(value = "/ice-server/conf/add/son", method = RequestMethod.POST)
    public Long confAddSon(@RequestParam Integer app,
                           @RequestBody IceConf conf,
                           @RequestParam Long parentId) {
        Long id = iceConfService.confAddSon(app, conf, parentId);
        iceServerService.updateByEdit();
        iceServerService.link(parentId, id);
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/add/forward", method = RequestMethod.POST)
    public Long confAddForward(@RequestParam Integer app,
                               @RequestBody IceConf conf,
                               @RequestParam Long nextId) {
        Long id = iceConfService.confAddForward(app, conf, nextId);
        iceServerService.updateByEdit();
        iceServerService.link(nextId, id);
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/add/son/ids", method = RequestMethod.POST)
    public List<Long> confAddSonIds(@RequestParam Integer app,
                                    @RequestParam Long parentId,
                                    @RequestParam String sonIds) {
        List<Long> ids = iceConfService.confAddSonIds(app, sonIds, parentId);
        iceServerService.updateByEdit();
        iceServerService.link(parentId, ids);
        return ids;
    }

    @RequestMapping(value = "/ice-server/conf/add/forward/id", method = RequestMethod.POST)
    public Long confAddForwardId(@RequestParam Integer app,
                                 @RequestParam Long forwardId,
                                 @RequestParam Long nextId) {
        Long id = iceConfService.confAddForwardId(app, forwardId, nextId);
        iceServerService.updateByEdit();
        iceServerService.link(nextId, forwardId);
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/edit/id", method = RequestMethod.POST)
    public Long confEditId(@RequestParam Integer app,
                           @RequestParam Long nodeId,
                           @RequestParam Long exchangeId,
                           @RequestParam(required = false) Long parentId,
                           @RequestParam(required = false) Long nextId,
                           @RequestParam(required = false) Integer index) {
        Long id = iceConfService.confEditId(app, nodeId, exchangeId, parentId, nextId, index);
        iceServerService.updateByEdit();
        if (parentId != null) {
            iceServerService.unlink(parentId, nodeId);
            iceServerService.link(parentId, exchangeId);
        }
        if (nextId != null) {
            iceServerService.unlink(nextId, nodeId);
            iceServerService.link(nextId, exchangeId);
        }
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/edit", method = RequestMethod.POST)
    public Long confEdit(@RequestParam Integer app,
                         @RequestBody IceConf conf) {
        Long id = iceConfService.confEdit(app, conf);
        iceServerService.updateByEdit();
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/forward/delete", method = RequestMethod.POST)
    public Long confForwardDelete(@RequestParam Integer app,
                                  @RequestParam Long forwardId,
                                  @RequestParam Long nextId) {
        Long id = iceConfService.confForwardDelete(app, forwardId, nextId);
        iceServerService.updateByEdit();
        iceServerService.unlink(nextId, forwardId);
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/son/move", method = RequestMethod.POST)
    public Long confSonMove(@RequestParam Integer app,
                            @RequestParam Long parentId,
                            @RequestParam Long sonId,
                            @RequestParam Integer originIndex,
                            @RequestParam Integer toIndex) {
        Long id = iceConfService.confSonMove(app, parentId, sonId, originIndex, toIndex);
        iceServerService.updateByEdit();
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/son/delete", method = RequestMethod.POST)
    public Long confSonDelete(@RequestParam Integer app,
                              @RequestParam Long sonId,
                              @RequestParam Long parentId,
                              @RequestParam Integer index) {
        Long id = iceConfService.confSonDelete(app, sonId, parentId, index);
        iceServerService.updateByEdit();
        iceServerService.unlink(parentId, sonId);
        return id;
    }

    @RequestMapping(value = "/ice-server/conf/leaf/class", method = RequestMethod.GET)
    public List<IceLeafClass> getConfLeafClass(@RequestParam Integer app, @RequestParam Byte type) {
        return iceConfService.getConfLeafClass(app, type);
    }

    @RequestMapping(value = "/ice-server/conf/class/check", method = RequestMethod.GET)
    public WebResult<String> leafClassCheck(@RequestParam Integer app, @RequestParam String clazz, @RequestParam Byte type) {
        return WebResult.success(iceConfService.leafClassCheck(app, clazz, type));
    }

    @RequestMapping(value = "/ice-server/conf/detail", method = RequestMethod.GET)
    public IceClientConf confDetail(@RequestParam Integer app, @RequestParam Long confId) {
        return iceConfService.confDetail(app, confId);
    }
}

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

    @RequestMapping(value = "/ice-server/conf/edit", method = RequestMethod.POST)
    public Long confEdit(@RequestBody IceConf conf, @RequestParam(required = false) Long parentId, @RequestParam(required = false) Long nextId) {
        //conf node edit-delete just pick up from parent
        conf.setStatus((byte) 1);
        Long id = iceConfService.confEdit(conf, parentId, nextId);
        iceServerService.updateByEdit();
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

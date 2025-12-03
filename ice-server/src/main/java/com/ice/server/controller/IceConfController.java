package com.ice.server.controller;

import com.ice.common.dto.IceTransferDto;
import com.ice.common.model.IceShowConf;
import com.ice.server.dao.model.IceBase;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.model.IceEditNode;
import com.ice.server.model.IceLeafClass;
import com.ice.server.service.IceAppService;
import com.ice.server.service.IceConfService;
import com.ice.server.service.IceServerService;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

/**
 * conf crud
 *
 * @author waitmoon
 */
@CrossOrigin
@RestController
public class IceConfController {
    @Autowired
    private IceConfService iceConfService;

    @Autowired
    private IceServerService iceServerService;

    @Autowired
    private IceAppService iceAppService;

    @RequestMapping(value = "/ice-server/conf/edit", method = RequestMethod.POST)
    public Long confEdit(@RequestBody IceEditNode editNode) {
        return iceConfService.confEdit(editNode);
    }

    @RequestMapping(value = "/ice-server/conf/leaf/class", method = RequestMethod.GET)
    public List<IceLeafClass> getConfLeafClass(@RequestParam Integer app, @RequestParam Byte type) {
        return iceConfService.getConfLeafClass(app, type);
    }

    @RequestMapping(value = "/ice-server/conf/class/check", method = RequestMethod.GET)
    public void leafClassCheck(@RequestParam Integer app, @RequestParam String clazz, @RequestParam Byte type) {
        iceConfService.leafClassCheck(app, clazz, type);
    }

    @RequestMapping(value = "/ice-server/conf/detail", method = RequestMethod.GET)
    public IceShowConf confDetail(@RequestParam Integer app, @RequestParam Long iceId, @RequestParam(required = false) Long confId, @RequestParam(defaultValue = "server") String address) {
        IceBase base = iceServerService.getActiveBaseById(app, iceId);
        if (base == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|iceId");
        }
        IceShowConf showConf = iceConfService.confDetail(app, confId == null ? base.getConfId() : confId, address, iceId);
        showConf.setIceId(iceId);
        showConf.setRegisterClients(iceAppService.getRegisterClients(app));
        return showConf;
    }

    @RequestMapping(value = "/ice-server/conf/release", method = RequestMethod.GET)
    public List<String> release(@RequestParam Integer app,
                                @RequestParam Long iceId) {
        IceTransferDto transferDto = iceServerService.release(app, iceId);
        // 基于文件系统，客户端通过轮询自动获取更新，无需推送
        return Collections.emptyList();
    }

    @RequestMapping(value = "/ice-server/conf/update_clean", method = RequestMethod.GET)
    public void updateClean(@RequestParam Integer app,
                            @RequestParam Long iceId) {
        iceServerService.updateClean(app, iceId);
    }
}

package com.ice.server.controller;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.server.exception.ErrorCode;
import com.ice.server.exception.ErrorCodeException;
import com.ice.server.nio.IceNioClientManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @author waitmoon
 */

@RestController
public class IceMockController {

    @Resource
    private IceNioClientManager iceNioClientManager;

    @RequestMapping(value = "/ice/nio/mock", method = RequestMethod.POST)
    public List<IceContext> nioMock(@RequestParam Integer app, @RequestBody IcePack pack) {
        if (app <= 0 || pack == null) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|pack");
        }
        if (pack.getIceId() <= 0 && !StringUtils.hasLength(pack.getScene()) && pack.getConfId() <= 0) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "iceId, scene and confId cannot be empty at the same time");
        }
        return iceNioClientManager.mock(app, pack);
    }

    @RequestMapping(value = "/ice/nio/mocks", method = RequestMethod.POST)
    public List<IceContext> nioMocks(@RequestParam Integer app, @RequestBody List<IcePack> packs) {
        if (app <= 0 || CollectionUtils.isEmpty(packs)) {
            throw new ErrorCodeException(ErrorCode.INPUT_ERROR, "app|packs");
        }
        List<IceContext> result = new ArrayList<>();
        for (IcePack pack : packs) {
            List<IceContext> list = iceNioClientManager.mock(app, pack);
            if (!CollectionUtils.isEmpty(list)) {
                result.addAll(list);
            }
        }
        return result;
    }
}

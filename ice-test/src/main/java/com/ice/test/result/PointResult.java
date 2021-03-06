package com.ice.test.result;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamResult;
import com.ice.test.service.SendService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Resource;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointResult extends BaseLeafRoamResult {

    @Resource
    private SendService sendService2;

    private String key;

    private double value;

    @Override
    protected boolean doRoamResult(IceRoam roam) {
        Integer uid = roam.getMulti(key);
        if (uid == null || value <= 0) {
            return false;
        }
        boolean res = sendService2.sendPoint(uid, value);
        roam.put("SEND_POINT", res);
        return res;
    }
}

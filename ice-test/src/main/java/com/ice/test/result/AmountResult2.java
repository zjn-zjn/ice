package com.ice.test.result;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackResult;
import com.ice.test.service.SendService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Resource;

@Data
@EqualsAndHashCode(callSuper = true)
public class AmountResult2 extends BaseLeafPackResult {

    @Resource
    private SendService sendService;

    private String key;

    private Object value;

    @Override
    protected boolean doPackResult(IcePack pack) {
        IceRoam roam = pack.getRoam();
        Integer uid = roam.getMulti(key);
        Double val = roam.getUnion(value);
        if (uid == null || val <= 0) {
            return false;
        }
        boolean res = sendService.sendAmount(uid, val);
        roam.putMulti("result." + "sendAmount", res);
        roam.putMulti("result." + "sendValue", val);
        roam.putMulti("result." + "scene", pack.getScene());
        return res;
    }
}

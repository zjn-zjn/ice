package com.ice.test.result;

import com.ice.core.annotation.IceField;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.test.service.SendService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PointResult2 extends BaseLeafResult {

    @Autowired
    private SendService sendService;
    //给谁发
    @IceField(name = "发给谁", desc = "发放的key 如uid")
    private String key;
    //发多少
    private Object value;

    @Override
    protected boolean doResult(IceRoam roam) {
        Number uid = roam.getDeep(key);
        if (uid == null) {
            return false;
        }
        Number value = roam.resolve(this.value);
        if (value == null || value.intValue() <= 0) {
            return false;
        }
        boolean res = sendService.sendPoint(uid.intValue(), value.intValue());
        roam.putDeep("result." + "sendPoint", value);
        roam.putDeep("result." + "scene", roam.getIceScene());
        return res;
    }
}

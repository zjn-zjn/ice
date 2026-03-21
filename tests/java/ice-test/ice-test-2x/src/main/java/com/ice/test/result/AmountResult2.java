package com.ice.test.result;

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
public class AmountResult2 extends BaseLeafResult {

    @Autowired
    private SendService sendService;
    //给谁发
    private String key;
    //发多少
    private Object value;

    @Override
    protected boolean doResult(IceRoam roam) {
        Integer uid = roam.getDeep(key);
        if (uid == null) {
            return false;
        }
        Integer value = roam.resolve(this.value);
        if (value <= 0) {
            return false;
        }
        boolean res = sendService.sendAmount(uid, value);
        roam.putDeep("result." + "sendAmount", value);
        roam.putDeep("result." + "scene", roam.getIceScene());
        return res;
    }
}

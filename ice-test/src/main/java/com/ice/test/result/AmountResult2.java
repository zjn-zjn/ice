package com.ice.test.result;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackResult;
import com.ice.test.service.SendService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AmountResult2 extends BaseLeafPackResult {

    @Autowired
    private SendService sendService;
    //给谁发
    private String key;
    //发多少
    private Object value;

    @Override
    protected boolean doPackResult(IcePack pack) {
        IceRoam roam = pack.getRoam();
        Integer uid = roam.getMulti(key);
        if (uid == null) {
            return false;
        }
        Integer value = roam.getUnion(this.value);
        if (value <= 0) {
            return false;
        }
        boolean res = sendService.sendAmount(uid, value);
        roam.putMulti("result." + "sendAmount", value);
        roam.putMulti("result." + "scene", pack.getScene());
        return res;
    }
}

package com.ice.test.result;

import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceNode;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamResult;
import com.ice.test.service.SendService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
@IceNode(name = "发放余额节点", desc = "用于发放余额")
public class AmountResult extends BaseLeafRoamResult {

    @Autowired
    private SendService sendService;
    @IceField(name = "发给谁", desc = "发放的key 如uid")
    private String key;
    @IceField(name = "发多少", desc = "发多少余额 如5")
    private double value;

    @Override
    protected boolean doRoamResult(IceRoam roam) {
        Integer uid = roam.getMulti(key);
        if (uid == null || value <= 0) {
            return false;
        }
        boolean res = sendService.sendAmount(uid, value);
        roam.put("SEND_AMOUNT", res);
        return res;
    }
}

package com.ice.test.result;

import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceNode;
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
@IceNode(name = "发放积分节点", desc = "用于发放积分奖励")
public class PointResult extends BaseLeafResult {

    @Autowired
    private SendService sendService2;
    @IceField(name = "发给谁", desc = "发放的key 如uid")
    private String key;
    @IceField(name = "发多少", desc = "发多少余额 如10")
    private double value;

    @Override
    protected boolean doResult(IceRoam roam) {
        Number uid = roam.getDeep(key);
        if (uid == null || value <= 0) {
            return false;
        }
        boolean res = sendService2.sendPoint(uid.intValue(), value);
        roam.put("SEND_POINT", res);
        return res;
    }
}

package com.ice.test.result;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Value;

@Data
@EqualsAndHashCode(callSuper = true)
public class InitResult extends BaseLeafRoamResult {

    @Value("${environment}")
    private String environment;

    private IceRoam initRoam;

    @Override
    protected boolean doRoamResult(IceRoam roam) {
        if (!"product".equals(environment)) {
            roam.putAll(initRoam);
            return true;
        }
        return false;
    }
}

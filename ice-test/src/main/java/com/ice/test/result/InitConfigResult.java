package com.ice.test.result;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.factory.annotation.Value;

/**
 * 初始化加载配置节点
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InitConfigResult extends BaseLeafRoamResult {

    @Value("${environment}")
    private String activeEnvironment;

    private IceRoam initRoamConfig;

    private String configEnvironment;

    @Override
    protected boolean doRoamResult(IceRoam roam) {
        if (activeEnvironment.equals(configEnvironment)) {
            roam.putAll(initRoamConfig);
            return true;
        }
        return false;
    }
}

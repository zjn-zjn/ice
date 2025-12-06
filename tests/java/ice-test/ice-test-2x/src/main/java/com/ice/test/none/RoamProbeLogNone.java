package com.ice.test.none;

import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceNode;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamNone;
import com.ice.core.utils.JacksonUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
@IceNode(name = "roam探针日志节点", desc = "用于探测roam中的值")
public class RoamProbeLogNone extends BaseLeafRoamNone {

    @IceField(name = "探针", desc = "探针为空则打印整个roam")
    private String key;

    @Override
    protected void doRoamNone(IceRoam roam) {
        if (StringUtils.hasLength(key)) {
            log.info("probe with key:{} :{}", key, JacksonUtils.toJsonString(roam.getMulti(key)));
        } else {
            log.info("probe :{}", JacksonUtils.toJsonString(roam));
        }
    }
}

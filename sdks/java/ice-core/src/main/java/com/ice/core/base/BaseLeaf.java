package com.ice.core.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.context.IceRoam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * @author waitmoon
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeaf extends BaseNode {

    /*
     * process node
     * @return process result
     */
    @Override
    protected NodeRunStateEnum processNode(IceRoam roam) {
        return doLeaf(roam);
    }

    /*
     * process leaf
     */
    protected abstract NodeRunStateEnum doLeaf(IceRoam roam);
}

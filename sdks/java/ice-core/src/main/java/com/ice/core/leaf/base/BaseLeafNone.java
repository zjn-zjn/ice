package com.ice.core.leaf.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseLeaf;
import com.ice.core.context.IceRoam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 * None leaf
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafNone extends BaseLeaf {

    /*
     * process leaf none
     */
    @Override
    protected NodeRunStateEnum doLeaf(IceRoam roam) {
        doNone(roam);
        return NodeRunStateEnum.NONE;
    }

    /*
     * process leaf none
     */
    protected abstract void doNone(IceRoam roam);
}

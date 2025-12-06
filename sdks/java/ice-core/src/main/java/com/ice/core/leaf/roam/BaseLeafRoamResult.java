package com.ice.core.leaf.roam;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafRoamResult extends BaseLeafPackResult {

    @Override
    protected boolean doPackResult(IcePack pack) {
        return doRoamResult(pack.getRoam());
    }

    /*
     * process leaf result with roam
     */
    protected abstract boolean doRoamResult(IceRoam roam);
}

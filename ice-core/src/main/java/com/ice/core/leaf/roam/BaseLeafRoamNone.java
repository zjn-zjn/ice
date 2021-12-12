package com.ice.core.leaf.roam;

import com.ice.core.context.IcePack;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.pack.BaseLeafPackNone;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafRoamNone extends BaseLeafPackNone {

    @Override
    protected void doPackNone(IcePack pack) {
        doRoamNone(pack.getRoam());
    }

    /*
     * process leaf none with roam
     */
    protected abstract void doRoamNone(IceRoam roam);
}

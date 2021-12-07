package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackFlow extends BaseLeafFlow {

    @Override
    protected boolean doFlow(IceContext cxt) {
        return doPackFlow(cxt.getPack());
    }

    /*
     * process leaf flow with pack
     *
     * @param pack
     * @return
     */
    protected abstract boolean doPackFlow(IcePack pack);
}

package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackFlow extends BaseLeafFlow {

    @Override
    protected boolean doFlow(IceContext ctx) {
        return doPackFlow(ctx.getPack());
    }

    /*
     * process leaf flow with pack
     */
    protected abstract boolean doPackFlow(IcePack pack);
}

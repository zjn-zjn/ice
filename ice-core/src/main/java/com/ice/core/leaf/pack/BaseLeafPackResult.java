package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackResult extends BaseLeafResult {

    @Override
    protected boolean doResult(IceContext ctx) {
        return doPackResult(ctx.getPack());
    }

    /*
     * process leaf result with pack
     */
    protected abstract boolean doPackResult(IcePack pack);
}

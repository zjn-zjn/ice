package com.ice.core.leaf.pack;

import com.ice.core.context.IceContext;
import com.ice.core.context.IcePack;
import com.ice.core.leaf.base.BaseLeafNone;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafPackNone extends BaseLeafNone {

    @Override
    protected void doNone(IceContext ctx) {
        doPackNone(ctx.getPack());
    }

    /*
     * process leaf none with pack
     */
    protected abstract void doPackNone(IcePack pack);
}

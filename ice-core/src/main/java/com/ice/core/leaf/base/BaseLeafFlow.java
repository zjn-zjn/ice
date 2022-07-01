package com.ice.core.leaf.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseLeaf;
import com.ice.core.context.IceContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 * Flow leaf
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeafFlow extends BaseLeaf {

    /*
     * process leaf flow
     */
    @Override
    protected NodeRunStateEnum doLeaf(IceContext ctx) {
        if (doFlow(ctx)) {
            return NodeRunStateEnum.TRUE;
        }
        return NodeRunStateEnum.FALSE;
    }

    /*
     * process leaf flow
     */
    protected abstract boolean doFlow(IceContext ctx);
}

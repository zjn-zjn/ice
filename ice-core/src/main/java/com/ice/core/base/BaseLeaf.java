package com.ice.core.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.context.IceContext;
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
    protected NodeRunStateEnum processNode(IceContext ctx) {
        return doLeaf(ctx);
    }

    /*
     * process leaf
     */
    protected abstract NodeRunStateEnum doLeaf(IceContext ctx);
}

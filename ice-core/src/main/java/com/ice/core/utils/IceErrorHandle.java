package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceContext;
import lombok.Data;

/**
 * @author waitmoon
 * IceErrorHandle
 * used on error occur in node
 */
@Data
public abstract class IceErrorHandle {

    private static IceErrorHandle handle = new DefaultIceErrorHandle();

    public static void setHandle(IceErrorHandle customHandle) {
        handle = customHandle;
    }

    public static NodeRunStateEnum handleError(BaseNode node, IceContext ctx, Throwable t) {
        return handle.handle(node, ctx, t);
    }

    protected abstract NodeRunStateEnum handle(BaseNode node, IceContext ctx, Throwable t);

    private static class DefaultIceErrorHandle extends IceErrorHandle {

        @Override
        protected NodeRunStateEnum handle(BaseNode node, IceContext ctx, Throwable t) {
            //default do noting and shutdown
            return NodeRunStateEnum.SHUT_DOWN;
        }
    }
}

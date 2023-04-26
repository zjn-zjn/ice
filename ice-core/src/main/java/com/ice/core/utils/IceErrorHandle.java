package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceContext;
import com.ice.core.handler.IceHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * @author waitmoon
 * IceErrorHandle
 * used on error occur in node/iceHandler
 */
@Data
@Slf4j
public abstract class IceErrorHandle {

    private static IceErrorHandle handle = new DefaultIceErrorHandle();

    public static void setHandle(IceErrorHandle customHandle) {
        handle = customHandle;
    }

    public static NodeRunStateEnum errorHandle(BaseNode node, IceContext ctx, Throwable t) {
        return handle.handle(node, ctx, t);
    }

    public static void errorHandle(IceHandler iceHandler, IceContext ctx, Throwable t) {
        handle.handle(iceHandler, ctx, t);
    }

    /**
     * handle node error
     *
     * @param node error node
     * @param ctx  error ctx
     * @param t    error
     * @return NodeRunStateEnum to control flow
     */
    protected abstract NodeRunStateEnum handle(BaseNode node, IceContext ctx, Throwable t);

    /**
     * handle iceHandler error
     *
     * @param iceHandler error iceHandler
     * @param ctx        error ctx
     * @param t          error
     */
    protected abstract void handle(IceHandler iceHandler, IceContext ctx, Throwable t);

    private static class DefaultIceErrorHandle extends IceErrorHandle {

        @Override
        protected NodeRunStateEnum handle(BaseNode node, IceContext ctx, Throwable t) {
            //default shutdown
            return NodeRunStateEnum.SHUT_DOWN;
        }

        @Override
        protected void handle(IceHandler iceHandler, IceContext ctx, Throwable t) {
            //default log the error
            if (t instanceof NodeException) {
                log.error("error iceId:{} nodeId:{} ctx:{}", iceHandler.getIceId(), JacksonUtils.toJsonString(ctx), ((NodeException) t).getNodeId(), t);
            } else {
                log.error("error iceId:{} ctx:{}", iceHandler.getIceId(), JacksonUtils.toJsonString(ctx), t);
            }
        }
    }
}

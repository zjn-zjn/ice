package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceMeta;
import com.ice.core.context.IceRoam;
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

    public static NodeRunStateEnum errorHandle(BaseNode node, IceRoam roam, Throwable t) {
        return handle.handle(node, roam, t);
    }

    public static void errorHandle(IceHandler iceHandler, IceRoam roam, Throwable t) {
        handle.handle(iceHandler, roam, t);
    }

    /**
     * handle node error
     *
     * @param node error node
     * @param roam error roam
     * @param t    error
     * @return NodeRunStateEnum to control flow
     */
    protected abstract NodeRunStateEnum handle(BaseNode node, IceRoam roam, Throwable t);

    /**
     * handle iceHandler error
     *
     * @param iceHandler error iceHandler
     * @param roam       error roam
     * @param t          error
     */
    protected abstract void handle(IceHandler iceHandler, IceRoam roam, Throwable t);

    private static class DefaultIceErrorHandle extends IceErrorHandle {

        @Override
        protected NodeRunStateEnum handle(BaseNode node, IceRoam roam, Throwable t) {
            //default shutdown
            return NodeRunStateEnum.SHUT_DOWN;
        }

        @Override
        protected void handle(IceHandler iceHandler, IceRoam roam, Throwable t) {
            String tp = "";
            String meta = "";
            if (roam != null) {
                IceMeta m = roam.getIceMeta();
                String trace = m.getTrace();
                tp = trace != null ? "[" + trace + "] " : "";
                meta = metaSuffix(m);
            }
            if (t instanceof NodeException) {
                log.error("{}error in node:{}{}", tp, ((NodeException) t).getNodeId(), meta, t);
            } else {
                log.error("{}error{}", tp, meta, t);
            }
        }

        private static String metaSuffix(IceMeta meta) {
            StringBuilder sb = new StringBuilder();
            if (meta.getId() > 0) {
                sb.append(" id=").append(meta.getId());
            }
            if (meta.getScene() != null && !meta.getScene().isEmpty()) {
                sb.append(" scene=").append(meta.getScene());
            }
            if (meta.getNid() > 0) {
                sb.append(" nid=").append(meta.getNid());
            }
            sb.append(" ts=").append(meta.getTs());
            return sb.toString();
        }
    }
}

package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
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
            String meta = "";
            if (roam != null) {
                meta = metaSuffix(roam);
            }
            if (t instanceof NodeException) {
                log.error("node error nodeId:{}{}", ((NodeException) t).getNodeId(), meta, t);
            } else {
                log.error("handler error{}", meta, t);
            }
        }

        private static String metaSuffix(IceRoam roam) {
            StringBuilder sb = new StringBuilder();
            if (roam.getId() > 0) {
                sb.append(" id=").append(roam.getId());
            }
            String scene = roam.getScene();
            if (scene != null && !scene.isEmpty()) {
                sb.append(" scene=").append(scene);
            }
            if (roam.getNid() > 0) {
                sb.append(" nid=").append(roam.getNid());
            }
            sb.append(" ts=").append(roam.getTs());
            return sb.toString();
        }
    }
}

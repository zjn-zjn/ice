package com.ice.core.handler;


import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceErrorHandle;
import com.ice.core.utils.IceTimeUtils;
import com.ice.core.utils.JacksonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * @author waitmoon
 * the handler find by scene/iceId
 */
@Slf4j
@Data
public final class IceHandler {

    /*
     * iceId(db base_id)
     */
    private long iceId;

    private long confId;

    /*
     * triggered scenes
     */
    private Set<String> scenes;
    /*
     * @see TimeTypeEnum
     */
    private TimeTypeEnum timeTypeEnum;

    private long start;

    private long end;

    /*
     * handler's debug
     * control pack process roam print
     */
    private byte debug;

    /*
     * handler exec root node
     */
    private BaseNode root;

    public void handle(IceContext ctx) {
        if (DebugEnum.filter(DebugEnum.IN_PACK, debug)) {
            log.info("handle id:{} in pack:{}", this.iceId, JacksonUtils.toJsonString(ctx.getPack()));
        }
        if (IceTimeUtils.timeDisable(timeTypeEnum, ctx.getPack().getRequestTime(), start, end)) {
            return;
        }
        try {
            if (root != null) {
                root.process(ctx);
                if (DebugEnum.filter(DebugEnum.PROCESS, debug)) {
                    log.info("handle id:{} process:{}", this.iceId, ctx.getProcessInfo().toString());
                }
                if (DebugEnum.filter(DebugEnum.OUT_PACK, debug)) {
                    log.info("handle id:{} out pack:{}", this.iceId, JacksonUtils.toJsonString(ctx.getPack()));
                } else {
                    if (DebugEnum.filter(DebugEnum.OUT_ROAM, debug)) {
                        log.info("handle id:{} out roam:{}", this.iceId, JacksonUtils.toJsonString(ctx.getPack().getRoam()));
                    }
                }
            } else {
                log.error("root not exist please check! iceId:{}", this.iceId);
            }
        } catch (Throwable t) {
            IceErrorHandle.errorHandle(this, ctx, t);
            throw t;
        }
    }

    public void handleWithConfId(IceContext ctx) {
        if (DebugEnum.filter(DebugEnum.IN_PACK, debug)) {
            log.info("handle confId:{} in pack:{}", this.confId, JacksonUtils.toJsonString(ctx.getPack()));
        }
        try {
            root.process(ctx);
            if (DebugEnum.filter(DebugEnum.PROCESS, debug)) {
                log.info("handle confId:{} process:{}", this.confId, ctx.getProcessInfo().toString());
            }
            if (DebugEnum.filter(DebugEnum.OUT_PACK, debug)) {
                log.info("handle confId:{} out pack:{}", this.confId, JacksonUtils.toJsonString(ctx.getPack()));
            } else {
                if (DebugEnum.filter(DebugEnum.OUT_ROAM, debug)) {
                    log.info("handle confId:{} out roam:{}", this.confId, JacksonUtils.toJsonString(ctx.getPack().getRoam()));
                }
            }
        } catch (NodeException ne) {
            log.error("error occur in node confId:{} node:{} ctx:{}", this.confId, ne.getNodeId(), JacksonUtils.toJsonString(ctx), ne);
        } catch (Exception e) {
            log.error("error occur confId:{} ctx:{}", this.confId, JacksonUtils.toJsonString(ctx), e);
        }
    }

    public Long getIceId() {
        return this.iceId;
    }

    public long findIceId() {
        return this.iceId;
    }

    /*
     * handler`s debug enum
     * controls the printing of the input and output parameters of the execution process
     */
    private enum DebugEnum {
        /*
         * enter PACK 1
         */
        IN_PACK,
        /*
         * execution process (used together with node debug) 2
         */
        PROCESS,
        /*
         * finale ROAM 4
         */
        OUT_ROAM,
        /*
         * finale PACK 8
         */
        OUT_PACK;

        private final byte mask;

        DebugEnum() {
            this.mask = (byte) (1 << ordinal());
        }

        public static boolean filter(DebugEnum debugEnum, byte debug) {
            return (debugEnum.mask & debug) != 0;
        }
    }
}

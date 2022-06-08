package com.ice.core.handler;

import com.alibaba.fastjson.JSON;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceTimeUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * @author zjn
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
     * handler的debug
     * control pack process roam print
     */
    private byte debug;

    /*
     * 执行根节点
     */
    private BaseNode root;

    public void handle(IceContext cxt) {
        if (DebugEnum.filter(DebugEnum.IN_PACK, debug)) {
            log.info("handle id:{} in pack:{}", this.iceId, JSON.toJSONString(cxt.getPack()));
        }
        if (IceTimeUtils.timeDisable(timeTypeEnum, cxt.getPack().getRequestTime(), start, end)) {
            return;
        }
        try {
            if (root != null) {
                root.process(cxt);
                if (DebugEnum.filter(DebugEnum.PROCESS, debug)) {
                    log.info("handle id:{} process:{}", this.iceId, cxt.getProcessInfo().toString());
                }
                if (DebugEnum.filter(DebugEnum.OUT_PACK, debug)) {
                    log.info("handle id:{} out pack:{}", this.iceId, JSON.toJSONString(cxt.getPack()));
                } else {
                    if (DebugEnum.filter(DebugEnum.OUT_ROAM, debug)) {
                        log.info("handle id:{} out roam:{}", this.iceId, JSON.toJSONString(cxt.getPack().getRoam()));
                    }
                }
            } else {
                log.error("root not exist please check! iceId:{}", this.iceId);
            }
        } catch (NodeException ne) {
            log.error("error occur in node iceId:{} node:{} cxt:{}", this.iceId, ne.getNodeId(), JSON.toJSONString(cxt), ne);
        } catch (Exception e) {
            log.error("error occur iceId:{} cxt:{}", this.iceId, JSON.toJSONString(cxt), e);
        }
    }

    public void handleWithConfId(IceContext cxt) {
        if (DebugEnum.filter(DebugEnum.IN_PACK, debug)) {
            log.info("handle confId:{} in pack:{}", this.confId, JSON.toJSONString(cxt.getPack()));
        }
        try {
            root.process(cxt);
            if (DebugEnum.filter(DebugEnum.PROCESS, debug)) {
                log.info("handle confId:{} process:{}", this.confId, cxt.getProcessInfo().toString());
            }
            if (DebugEnum.filter(DebugEnum.OUT_PACK, debug)) {
                log.info("handle confId:{} out pack:{}", this.confId, JSON.toJSONString(cxt.getPack()));
            } else {
                if (DebugEnum.filter(DebugEnum.OUT_ROAM, debug)) {
                    log.info("handle confId:{} out roam:{}", this.confId, JSON.toJSONString(cxt.getPack().getRoam()));
                }
            }
        } catch (NodeException ne) {
            log.error("error occur in node confId:{} node:{} cxt:{}", this.confId, ne.getNodeId(), JSON.toJSONString(cxt), ne);
        } catch (Exception e) {
            log.error("error occur confId:{} cxt:{}", this.confId, JSON.toJSONString(cxt), e);
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

package com.ice.core.handler;


import com.ice.common.enums.TimeTypeEnum;
import com.ice.common.exception.NodeException;
import com.ice.core.base.BaseNode;
import com.ice.core.context.IceMeta;
import com.ice.core.context.IceRoam;
import com.ice.core.utils.IceErrorHandle;
import com.ice.core.utils.IceTimeUtils;
import com.ice.core.utils.JacksonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
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
     * control roam process print
     */
    private byte debug;

    /*
     * handler exec root node
     */
    private BaseNode root;

    public void handle(IceRoam roam) {
        String trace = roam.getIceTrace();
        String tp = trace != null ? "[" + trace + "] " : "";
        if (trace != null) {
            MDC.put("traceId", trace);
        }
        try {
            if (IceTimeUtils.timeDisable(timeTypeEnum, roam.getIceTs(), start, end)) {
                return;
            }
            if (DebugEnum.filter(DebugEnum.IN_ROAM, debug)) {
                log.info("{}handle in roam:{}{}", tp, roamWithoutIce(roam), metaSuffix(roam));
            }
            if (root != null) {
                root.process(roam);
                if (DebugEnum.filter(DebugEnum.PROCESS, debug)) {
                    log.info("{}handle process:{}{}", tp, roam.getIceProcess().toString(), metaSuffix(roam));
                }
                if (DebugEnum.filter(DebugEnum.OUT_ROAM, debug)) {
                    log.info("{}handle out roam:{}{}", tp, roamWithoutIce(roam), metaSuffix(roam));
                }
            } else {
                log.error("{}root not exist{}", tp, metaSuffix(roam));
            }
        } catch (Throwable t) {
            IceErrorHandle.errorHandle(this, roam, t);
            throw t;
        } finally {
            MDC.remove("traceId");
        }
    }

    public void handleWithNodeId(IceRoam roam) {
        String trace = roam.getIceTrace();
        String tp = trace != null ? "[" + trace + "] " : "";
        if (trace != null) {
            MDC.put("traceId", trace);
        }
        try {
            if (DebugEnum.filter(DebugEnum.IN_ROAM, debug)) {
                log.info("{}handle in roam:{}{}", tp, roamWithoutIce(roam), metaSuffix(roam));
            }
            if (root != null) {
                root.process(roam);
                if (DebugEnum.filter(DebugEnum.PROCESS, debug)) {
                    log.info("{}handle process:{}{}", tp, roam.getIceProcess().toString(), metaSuffix(roam));
                }
                if (DebugEnum.filter(DebugEnum.OUT_ROAM, debug)) {
                    log.info("{}handle out roam:{}{}", tp, roamWithoutIce(roam), metaSuffix(roam));
                }
            } else {
                log.error("{}root not exist{}", tp, metaSuffix(roam));
            }
        } catch (Throwable t) {
            IceErrorHandle.errorHandle(this, roam, t);
            throw t;
        } finally {
            MDC.remove("traceId");
        }
    }

    private static String metaSuffix(IceRoam roam) {
        IceMeta meta = roam.getIceMeta();
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

    private static String roamWithoutIce(IceRoam roam) {
        Map<String, Object> data = new LinkedHashMap<>(roam);
        data.remove("_ice");
        return JacksonUtils.toJsonString(data);
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
         * input ROAM 1
         */
        IN_ROAM,
        /*
         * execution process (used together with node debug) 2
         */
        PROCESS,
        /*
         * output ROAM 4
         */
        OUT_ROAM;

        private final byte mask;

        DebugEnum() {
            this.mask = (byte) (1 << ordinal());
        }

        public static boolean filter(DebugEnum debugEnum, byte debug) {
            return (debugEnum.mask & debug) != 0;
        }
    }
}

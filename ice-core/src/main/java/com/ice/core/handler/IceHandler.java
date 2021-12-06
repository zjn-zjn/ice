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
 * 通过scene和iceId获取到的由配置产生的具体的执行者
 */
@Slf4j
@Data
public final class IceHandler {

    /*
     * iceId
     */
    private long iceId;

    /*
     * 场景
     */
    private Set<String> scenes;
    /*
     * 时间类型
     *
     * @see TimeTypeEnum
     */
    private TimeTypeEnum timeTypeEnum;
    /*
     * 开始时间
     */
    private long start;
    /*
     * 结束时间
     */
    private long end;

    /*
     * handler的debug
     * 控制着入参,出参与执行过程的打印
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
        if (IceTimeUtils.timeEnable(timeTypeEnum, cxt.getPack().getRequestTime(), start, end)) {
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
            log.error("error occur in node iceId:{} node:{} cxt:{}", this.iceId, cxt.getCurrentId(), JSON.toJSONString(cxt), ne);
        } catch (Exception e) {
            log.error("error occur iceId:{} node:{} cxt:{}", this.iceId, cxt.getCurrentId(), JSON.toJSONString(cxt), e);
        }
    }

    public Long getIceId() {
        return this.iceId;
    }

    public long findIceId() {
        return this.iceId;
    }

    /*
     * handler的debug枚举
     * 控制着入参,出参与执行过程的打印
     */
    private enum DebugEnum {
        /*
         * 入参PACK 1
         */
        IN_PACK,
        /*
         * 执行过程(和节点debug一并使用) 2
         */
        PROCESS,
        /*
         * 结局ROAM 4
         */
        OUT_ROAM,
        /*
         * 结局PACK 8
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

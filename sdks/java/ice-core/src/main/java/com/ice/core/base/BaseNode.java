package com.ice.core.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.annotation.IceIgnore;
import com.ice.core.context.IceRoam;
import com.ice.core.utils.IceErrorHandle;
import com.ice.core.utils.IceTimeUtils;
import com.ice.core.utils.ProcessUtils;
import lombok.Data;

/**
 * @author waitmoon
 * ice base node
 * Note: it should be avoided to be consistent with the basic field during development
 */
@Data
public abstract class BaseNode {
    /*
     * nodeId
     */
    @IceIgnore
    private long iceNodeId;
    /*
     * time type
     */
    @IceIgnore
    private TimeTypeEnum iceTimeTypeEnum;
    /*
     * node start run time
     */
    @IceIgnore
    private long iceStart;
    /*
     * node end run time
     */
    @IceIgnore
    private long iceEnd;
    /*
     * inverse
     * 1.only effect TRUE&FALSE
     * 2.not effect on OUT_TIME&NONE
     */
    @IceIgnore
    private boolean iceInverse;
    /*
     * forward node
     * if forward return FALSE then this node reject run
     * forward node the same of combined with relation-and
     */
    @IceIgnore
    private BaseNode iceForward;

    @IceIgnore
    private String iceLogName;
    /*
     * node error handle res from config
     * this config is high priority than custom error handle method
     */
    @IceIgnore
    private NodeRunStateEnum iceErrorStateEnum;

    @IceIgnore
    private byte iceType;

    /*
     * process
     * @return NodeRunStateEnum
     */
    public NodeRunStateEnum process(IceRoam roam) {
        if (IceTimeUtils.timeDisable(iceTimeTypeEnum, roam.getIceTs(), iceStart, iceEnd)) {
            ProcessUtils.collectInfo(roam.getIceProcess(), this, 'O');
            return NodeRunStateEnum.NONE;
        }
        long start = System.currentTimeMillis();
        NodeRunStateEnum res;
        try {
            if (iceForward != null) {
                //process forward
                NodeRunStateEnum forwardRes = iceForward.process(roam);
                if (forwardRes != NodeRunStateEnum.FALSE) {
                    //forward return not false then process this
                    res = processNode(roam);
                    //forward just like node with and relation, return like and also
                    res = forwardRes == NodeRunStateEnum.NONE ? res : (res == NodeRunStateEnum.NONE ? NodeRunStateEnum.TRUE : res);
                    ProcessUtils.collectInfo(roam.getIceProcess(), this, start, res);
                    return iceInverse ?
                            res == NodeRunStateEnum.TRUE ?
                                    NodeRunStateEnum.FALSE :
                                    res == NodeRunStateEnum.FALSE ? NodeRunStateEnum.TRUE : res :
                            res;
                }
                ProcessUtils.collectRejectInfo(roam.getIceProcess(), this);
                return NodeRunStateEnum.FALSE;
            }
            res = processNode(roam);
        } catch (Throwable t) {
            /*error occur use error handle method*/
            NodeRunStateEnum errorRunState = errorHandle(roam, t);
            if (this.iceErrorStateEnum != null) {
                /*error handle in config is high priority then error method return*/
                errorRunState = this.iceErrorStateEnum;
            }
            if (errorRunState == null || errorRunState == NodeRunStateEnum.SHUT_DOWN) {
                /*shutdown process and throw e*/
                ProcessUtils.collectInfo(roam.getIceProcess(), this, start, NodeRunStateEnum.SHUT_DOWN);
                throw t;
            } else {
                /*error but continue*/
                res = errorRunState;
            }
        }
        ProcessUtils.collectInfo(roam.getIceProcess(), this, start, res);
        return iceInverse ?
                res == NodeRunStateEnum.TRUE ?
                        NodeRunStateEnum.FALSE :
                        res == NodeRunStateEnum.FALSE ? NodeRunStateEnum.TRUE : res :
                res;
    }

    /*
     * process node
     */
    protected abstract NodeRunStateEnum processNode(IceRoam roam);

    public NodeRunStateEnum errorHandle(IceRoam roam, Throwable t) {
        return IceErrorHandle.errorHandle(this, roam, t);
    }

    public Long getIceNodeId() {
        return iceNodeId;
    }

    public long findIceNodeId() {
        return iceNodeId;
    }

    public void afterPropertiesSet() {
    }
}

package com.ice.core.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceTimeUtils;
import com.ice.core.utils.ProcessUtils;
import lombok.Data;

/**
 * @author zjn
 * 基础Node
 * 注意:开发时应避免与基础字段一致
 */
@Data
public abstract class BaseNode {
    /*
     * nodeId
     */
    private long iceNodeId;
    /*
     * time type
     */
    private TimeTypeEnum iceTimeTypeEnum;
    /*
     * node start run time
     */
    private long iceStart;
    /*
     * node end run time
     */
    private long iceEnd;
    /*
     * iceNodeDebug(print process info)
     */
    private boolean iceNodeDebug;
    /*
     * inverse
     * 1.only effect TRUE&FALSE
     * 2.not effect on OUT_TIME&NONE
     */
    private boolean iceInverse;
    /*
     * forward node
     * if forward return FALSE then this node reject run
     * forward node the same of combined with relation-and
     */
    private BaseNode iceForward;
    /*
     * sync lock default not work
     */
    private boolean iceLock;
    /*
     * transaction default not work
     */
    private boolean iceTransaction;

    private String iceLogName;

    /*
     * process
     * @return NodeRunStateEnum
     */
    public NodeRunStateEnum process(IceContext cxt) {
        if (IceTimeUtils.timeDisable(iceTimeTypeEnum, cxt.getPack().getRequestTime(), iceStart, iceEnd)) {
            ProcessUtils.collectInfo(cxt.getProcessInfo(), this, 'O');
            return NodeRunStateEnum.NONE;
        }
        long start = System.currentTimeMillis();
        if (iceForward != null) {
            NodeRunStateEnum forwardRes = iceForward.process(cxt);
            if (forwardRes != NodeRunStateEnum.FALSE) {
                NodeRunStateEnum res = processNode(cxt);
                res = forwardRes == NodeRunStateEnum.NONE ? res : (res == NodeRunStateEnum.NONE ? NodeRunStateEnum.TRUE : res);
                ProcessUtils.collectInfo(cxt.getProcessInfo(), this, start, res);
                return iceInverse ?
                        res == NodeRunStateEnum.TRUE ?
                                NodeRunStateEnum.FALSE :
                                res == NodeRunStateEnum.FALSE ? NodeRunStateEnum.TRUE : res :
                        res;
            }
            ProcessUtils.collectRejectInfo(cxt.getProcessInfo(), this);
            return NodeRunStateEnum.FALSE;
        }
        NodeRunStateEnum res = processNode(cxt);
        ProcessUtils.collectInfo(cxt.getProcessInfo(), this, start, res);
        return iceInverse ?
                res == NodeRunStateEnum.TRUE ?
                        NodeRunStateEnum.FALSE :
                        res == NodeRunStateEnum.FALSE ? NodeRunStateEnum.TRUE : res :
                res;
    }

    /*
     * process node
     */
    protected abstract NodeRunStateEnum processNode(IceContext cxt);

    public Long getIceNodeId() {
        return iceNodeId;
    }

    public long findIceNodeId() {
        return iceNodeId;
    }
}

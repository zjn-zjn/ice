package com.ice.core.utils;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.base.BaseNode;

/**
 * @author zjn
 * 过程信息组装类
 */
public final class ProcessUtils {

    private ProcessUtils() {
    }

    /*
     * node run state
     * O outOfTime (not in execute time)
     * E error
     * T true F false
     * R reject(the forward return false)
     * N None
     * [iceNodeId:process class name:process return:time used]
     * remarks:
     * 1.-INV inverse active
     */
    public static void collectInfo(StringBuilder sb, BaseNode node, long start, NodeRunStateEnum stateEnum) {
        if (node.isIceNodeDebug()) {
            char state;
            switch (stateEnum) {
                case FALSE:
                    state = 'F';
                    break;
                case TRUE:
                    state = 'T';
                    break;
                case NONE:
                    state = 'N';
                    break;
                default:
                    state = '?';
                    break;
            }
            sb.append('[').append(node.findIceNodeId()).append(':').append(node.getIceLogName() == null ? node.getClass().getSimpleName() : node.getIceLogName()).append(':')
                    .append(state).append(node.isIceInverse() ? "-INV:" : ':').append(System.currentTimeMillis() - start)
                    .append(']');
        }
    }

    /*
     * [iceNodeId:process class name:process return]
     */
    public static void collectInfo(StringBuilder sb, BaseNode node, char state) {
        if (node.isIceNodeDebug()) {
            sb.append('[').append(node.findIceNodeId()).append(':').append(node.getIceLogName() == null ? node.getClass().getSimpleName() : node.getIceLogName()).append(':')
                    .append(state).append(']');
        }
    }

    /*
     * reject info
     */
    public static void collectRejectInfo(StringBuilder sb, BaseNode node) {
        if (node.isIceNodeDebug()) {
            sb.append('[').append(node.findIceNodeId()).append(':').append(node.getIceLogName() == null ? node.getClass().getSimpleName() : node.getIceLogName()).append(':')
                    .append("R-F").append(']');
        }
    }
}


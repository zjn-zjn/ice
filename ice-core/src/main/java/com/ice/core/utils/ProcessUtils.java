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

  /**
   * 节点运行情况
   * <p>
   * O outOfTime (不在节点执行时间)
   * E error (错误)
   * T true F false  (flow&result节点)
   * R reject(前置节点未通过)
   * N None节点 或没有子节点的关系节点,或全是None节点的非True节点的关系节点
   * <p>
   * [节点ID:执行类名:执行结果:执行耗时(以handler开始执行到本节点执行完毕时间)]
   * <p>
   * 另:
   * 1.-INV表示该节点要反转
   * 2.节点的执行时间为相邻节点的差值
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

  /**
   * 节点运行情况
   * <p>
   * O outOfTime (不在节点执行时间)
   * E error (错误)
   * T true F false  (flow&result节点)
   * R reject(前置节点未通过)
   * N None节点 或没有子节点的关系节点,或全是None节点的非True节点的关系节点
   * <p>
   * [节点ID:执行类名:执行结果]
   */
  public static void collectInfo(StringBuilder sb, BaseNode node, char state) {
    if (node.isIceNodeDebug()) {
      sb.append('[').append(node.findIceNodeId()).append(':').append(node.getIceLogName() == null ? node.getClass().getSimpleName() : node.getIceLogName()).append(':')
          .append(state).append(']');
    }
  }

  /**
   * 拒绝执行信息
   *
   * @param sb
   * @param node
   */
  public static void collectRejectInfo(StringBuilder sb, BaseNode node) {
    if (node.isIceNodeDebug()) {
      sb.append('[').append(node.findIceNodeId()).append(':').append(node.getIceLogName() == null ? node.getClass().getSimpleName() : node.getIceLogName()).append(':')
          .append("R-F").append(']');
    }
  }
}


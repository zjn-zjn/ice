package com.ice.core.base;

import com.ice.common.enums.NodeRunStateEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceTimeUtils;
import com.ice.core.utils.ProcessUtils;
import lombok.Data;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 * 基础Node
 * 注意:开发时应避免与基础字段一致
 */
@Data
public abstract class BaseNode {
  /**
   * 节点ID
   */
  private long iceNodeId;
  /**
   * 时间类型
   */
  private TimeTypeEnum iceTimeTypeEnum;
  /**
   * 开始时间
   */
  private long iceStart;
  /**
   * 结束时间
   */
  private long iceEnd;
  /**
   * iceNodeDebug
   */
  private boolean iceNodeDebug;
  /**
   * 反转
   * 1.仅对TRUE和FALSE反转
   * 2.对OUTTIME,NONE的反转无效
   */
  private boolean iceInverse;
  /**
   * 前置节点
   * 如果前置节点返回FALSE,节点的执行将被拒绝
   * forward节点可以理解为是用AND连接的forward和this
   */
  private BaseNode iceForward;
  /**
   * 同步锁 默认不开启
   */
  private boolean iceLock;
  /**
   * 事务 默认不开启
   */
  private boolean iceTransaction;

  private String iceLogName;

  /**
   * process
   *
   * @param cxt 入参
   * @return true(f通过 r获得) false(f不通过 r丢失)
   */
  public NodeRunStateEnum process(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    cxt.setCurrentId(this.iceNodeId);
    if (IceTimeUtils.timeEnable(iceTimeTypeEnum, cxt.getPack().getRequestTime(), iceStart, iceEnd)) {
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

  /**
   * processNode
   *
   * @param cxt 入参
   * @return 节点执行结果
   */
  protected abstract NodeRunStateEnum processNode(IceContext cxt) throws InvocationTargetException, IllegalAccessException;

  public Long getIceNodeId() {
    return iceNodeId;
  }

  public long findIceNodeId() {
    return iceNodeId;
  }
}

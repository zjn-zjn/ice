package com.ice.core.base;

import com.ice.common.enums.ErrorHandleEnum;
import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.context.IceContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;

/**
 * @author zjn
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public abstract class BaseLeaf extends BaseNode {

  /**
   * 默认仅中止执行SHUT_DOWN
   */
  private ErrorHandleEnum iceErrorHandleEnum = ErrorHandleEnum.SHUT_DOWN;

  /**
   * processNode
   *
   * @param cxt 入参
   * @return 节点执行结果
   */
  @Override
  protected NodeRunStateEnum processNode(IceContext cxt) throws InvocationTargetException, IllegalAccessException {
    try {
      return doLeaf(cxt);
    } catch (Exception e) {
      switch (iceErrorHandleEnum) {
        case CONTINUE_NONE:
          if (this.isIceNodeDebug()) {
            log.error("error occur in {} handle with none", this.findIceNodeId());
          }
          return NodeRunStateEnum.NONE;
        case CONTINUE_FALSE:
          if (this.isIceNodeDebug()) {
            log.error("error occur in {} handle with false", this.findIceNodeId());
          }
          return NodeRunStateEnum.FALSE;
        case CONTINUE_TRUE:
          if (this.isIceNodeDebug()) {
            log.error("error occur in {} handle with true", this.findIceNodeId());
          }
          return NodeRunStateEnum.TRUE;
        case SHUT_DOWN:
          if (this.isIceNodeDebug()) {
            log.error("error occur in {} handle with shut down", this.findIceNodeId());
          }
          throw e;
        case SHUT_DOWN_STORE:
          if (this.isIceNodeDebug()) {
            log.error("error occur in {} handle with shut down store", this.findIceNodeId());
          }
          //TODO store
          throw e;
        default:
          throw e;
      }
    }
  }

  /**
   * process leaf
   *
   * @param cxt
   * @return
   */
  protected abstract NodeRunStateEnum doLeaf(IceContext cxt) throws InvocationTargetException, IllegalAccessException;
}

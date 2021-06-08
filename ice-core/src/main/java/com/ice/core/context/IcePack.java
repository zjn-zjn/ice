package com.ice.core.context;

import com.ice.common.enums.RequestTypeEnum;
import com.ice.common.utils.UUIDUtils;
import lombok.Data;
import lombok.ToString;

/**
 * @author zjn
 * 进入Ice执行的请求体
 */
@Data
@ToString
public final class IcePack {

  /**
   * 请求的 iceId
   */
  private long iceId;
  /**
   * 请求的场景 iceId为空时必填
   */
  private String scene;
  /**
   * 直接将confId作为root发起调用
   */
  private long confId;
  /**
   * 游荡字段
   * 一般执行过程中从这里取/放值
   */
  private volatile IceRoam roam = new IceRoam();
  /**
   * 请求类型 默认正式
   *
   * @see RequestTypeEnum
   */
  private int type = RequestTypeEnum.FORMAL.getType();
  /**
   * 请求时间
   */
  private long requestTime;
  /**
   * 追踪ID
   */
  private String traceId;
  /**
   * 优先级 如果为0则以执行的handler的优先级为准
   */
  private long priority;

  /**
   * 1.handler 最终以debug|handler.debug展示
   * 2.confRoot 最终以this.debug展示
   */
  private byte debug;

  public IcePack() {
    this.setTraceId(UUIDUtils.generateMost22UUID());
    this.requestTime = System.currentTimeMillis();
  }

  public IcePack(String traceId, long requestTime) {
    if (traceId == null || traceId.isEmpty()) {
      /*traceId为空时,生成traceId*/
      this.setTraceId(UUIDUtils.generateMost22UUID());
    } else {
      this.traceId = traceId;
    }
    if (requestTime <= 0) {
      this.requestTime = System.currentTimeMillis();
    } else {
      this.requestTime = requestTime;
    }
  }

  public IcePack(long requestTime) {
    /*traceId为空时,生成traceId*/
    this.setTraceId(UUIDUtils.generateMost22UUID());
    if (requestTime <= 0) {
      this.requestTime = System.currentTimeMillis();
    } else {
      this.requestTime = requestTime;
    }
  }

  public IcePack(String traceId) {
    if (traceId == null || traceId.isEmpty()) {
      /*traceId为空时,生成traceId*/
      this.setTraceId(UUIDUtils.generateMost22UUID());
    } else {
      this.traceId = traceId;
    }
    this.requestTime = System.currentTimeMillis();
  }

  public IcePack newPack(IceRoam roam) {
    IcePack pack = new IcePack(traceId, requestTime);
    pack.setIceId(iceId);
    pack.setScene(scene);
    if (roam != null) {
      /*此处没有用深拷贝*/
      pack.setRoam(new IceRoam(roam));
    }
    pack.setType(type);
    pack.setPriority(priority);
    return pack;
  }
}

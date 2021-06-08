package com.ice.server.service;

import com.ice.server.model.IceBaseVo;
import com.ice.server.model.IceConfVo;
import com.ice.server.model.WebResult;

/**
 * @author zjn
 */
public interface IceEditService {

  /**
   * get base
   *
   * @param app
   * @param pageIndex
   * @param pageSize
   * @return
   */
  WebResult getBase(Integer app, Integer pageIndex, Integer pageSize);

  /**
   * 编辑base
   *
   * @param baseVo
   * @return
   */
  WebResult editBase(IceBaseVo baseVo);

  /**
   * 编辑Conf
   *
   * @param app
   * @param type
   * @param iceId
   * @param confVo
   * @return
   */
  WebResult editConf(Integer app, Integer type, Long iceId, IceConfVo confVo);

  /**
   * 获取leafClass
   *
   * @param app
   * @param type
   * @return
   */
  WebResult getLeafClass(int app, byte type);

  /**
   * 发布
   *
   * @param app
   * @param iceId
   * @param reason
   * @return
   */
  WebResult push(Integer app, Long iceId, String reason);

  /**
   * 发布历史
   *
   * @param app
   * @param iceId
   * @return
   */
  WebResult history(Integer app, Long iceId);

  /**
   * 导出数据
   *
   * @param iceId
   * @param pushId
   * @return
   */
  WebResult exportData(Long iceId, Long pushId);

  /**
   * 回滚
   *
   * @param pushId
   * @return
   */
  WebResult rollback(Long pushId);

  /**
   * 导入数据
   *
   * @param data
   * @return
   */
  WebResult importData(String data);

//  /**
//   * ice复制
//   * @param data
//   * @return
//   */
//  WebResult copyData(String data);

}

package com.ice.server.service;

import com.ice.server.model.IceBaseVo;
import com.ice.server.model.IceConfVo;
import com.ice.server.model.WebResult;

/**
 * @author zjn
 */
@Deprecated
public interface IceEditService {

/*
   * get base
   *
   * @param app app
   * @param pageIndex 页码
   * @param pageSize 页大小
   */
  WebResult getBase(Integer app, Integer pageIndex, Integer pageSize);

/*
   * 编辑base
   *
   * @param baseVo 编辑vo
   */
  WebResult editBase(IceBaseVo baseVo);

/*
   * 编辑Conf
   *
   * @param app app
   * @param type 编辑类型
   * @param iceId iceId
   * @param confVo 配置Vo
   */
  WebResult editConf(Integer app, Integer type, Long iceId, IceConfVo confVo);

/*
   * 获取leafClass
   *
   * @param app app
   * @param type 叶子类型
   */
  WebResult getLeafClass(int app, byte type);

/*
   * 发布
   *
   * @param app app
   * @param iceId iceId
   * @param reason 发布原因
   */
  WebResult push(Integer app, Long iceId, String reason);

/*
   * 发布历史
   *
   * @param app app
   * @param iceId iceId
   */
  WebResult history(Integer app, Long iceId);

/*
   * 导出数据
   */
  WebResult exportData(Long iceId, Long pushId);

/*
   * 回滚
   */
  WebResult rollback(Long pushId);

/*
   * 导入数据
   *
   * @param data 导入json
   */
  WebResult importData(String data);

}

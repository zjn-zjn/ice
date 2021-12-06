package com.ice.server.service;

import com.ice.common.model.IceBaseDto;
import com.ice.common.model.IceConfDto;
import com.ice.server.controller.IceAppDto;
import com.ice.server.dao.model.IceBase;
import com.ice.server.dao.model.IceConf;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author zjn
 */
public interface IceServerService {

/*
   * 根据app获取生效中的Conf
   */
  Collection<IceConfDto> getActiveConfsByApp(Integer app);

/*
   * 根据app获取生效中的base
   */
  Collection<IceBaseDto> getActiveBasesByApp(Integer app);

/*
   * 根据app获取初始化json
   */
  String getInitJson(Integer app);

/*
   * 获取所有的activeBase-从缓存
   */
  List<IceBase> getBaseActive(Integer app);

/*
   * 获取所有的active-从库里
   */
  List<IceBase> getBaseFromDb(Integer app);

/*
   * 根据confId获取配置信息
   */
  IceConf getActiveConfById(Integer app, Long confId);

  Map<String, Integer> getLeafClassMap(Integer app, Byte type);

  void addLeafClass(Integer app, Byte type, String className);

  void updateByEdit();

  Set<Integer> getAppSet();

  List<IceAppDto> getAppList();
}

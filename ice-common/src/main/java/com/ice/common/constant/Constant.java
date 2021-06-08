package com.ice.common.constant;

import com.ice.common.utils.AddressUtils;

/**
 * @author zjn
 * 常量
 */
public final class Constant {

  private Constant() {
  }

  public static String getUpdateExchange() {
    return "ice.update.exchange";
  }

  public static String getShowConfExchange() {
    return "ice.show.conf.exchange";
  }

  public static String getMockExchange() {
    return "ice.mock.exchange";
  }

  public static String getInitExchange() {
    return "ice.init.exchange";
  }

  public static String getUpdateRoutetKey(Integer app) {
    return "ice.update." + app;
  }

  public static String getShowConfQueue(Integer app) {
    return "ice.show.conf." + app;
  }

  public static String getMockQueue(Integer app) {
    return "ice.mock." + app;
  }

  public static String genUpdateTmpQueue() {
    /*"ice.tmp.queue-" + host + ":" + port*/
    return "ice.tmp.queue-" + AddressUtils.getAddressPort();
  }
}

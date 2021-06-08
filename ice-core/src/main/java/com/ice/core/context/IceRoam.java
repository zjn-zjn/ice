package com.ice.core.context;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zjn
 * Ice中执行的游荡字段的结构
 * 在HashMap的基础上使用String化的key对Ice做拓展
 */
public final class IceRoam extends HashMap<String, Object> {

  private static final long serialVersionUID = 2241673818101234922L;

  public IceRoam(int initialCapacity, float loadFactor) {
    super(initialCapacity, loadFactor);
  }

  public IceRoam(int initialCapacity) {
    super(initialCapacity);
  }

  public IceRoam() {
  }

  public IceRoam(Map<String, Object> map) {
    super(map);
  }

  /**
   * 以'.'为介质Key存储层次化的自定义结构
   *
   * @param multiKey
   * @param value
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T putMulti(String multiKey, T value) {
    if (multiKey == null) {
      return (T) put(null, value);
    }
    String[] keys = multiKey.split("\\.");
    if (keys.length == 1) {
      /*只有一个*/
      return (T) put(keys[0], value);
    }
    Map<String, Object> endMap = this;
    Map<String, Object> forwardMap = this;
    int i = 0;
    for (; i < keys.length - 1; i++) {
      endMap = (Map<String, Object>) endMap.get(keys[i]);
      if (endMap == null) {
        int j = i;
        for (; j < keys.length - 1; j++) {
          endMap = new HashMap<>();
          forwardMap.put(keys[j], endMap);
          forwardMap = endMap;
        }
        i = j;
        break;
      } else {
        forwardMap = endMap;
      }
    }
    return (T) endMap.put(keys[i], value);
  }

  /**
   * 通过以'.'为介质的Key查找
   *
   * @param multiKey
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T getMulti(String multiKey) {
    if (multiKey == null) {
      return (T) get(null);
    }
    String[] keys = multiKey.split("\\.");
    if (keys.length == 1) {
      /*只有一个*/
      return (T) get(keys[0]);
    }
    Map<String, Object> endMap = this;
    int i = 0;
    for (; i < keys.length - 1; i++) {
      endMap = (Map<String, Object>) endMap.get(keys[i]);
      if (endMap == null) {
        return null;
      }
    }
    return (T) endMap.get(keys[i]);
  }

  @SuppressWarnings("unchecked")
  public <T> T getValue(Object key) {
    return (T) get(key);
  }

  /**
   * 多源联合获取值
   * 前缀是@的字符串指向roam内部获取数据
   *
   * @param union
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T getUnion(Object union) {
    if (union == null) {
      return null;
    }
    if (union instanceof String) {
      String key = (String) union;
      if (!(key).isEmpty() && (key).charAt(0) == '@') {
        return getUnion(getMulti(key.substring(1)));
      }
      return (T) union;
    }
    return (T) union;
  }

  @SuppressWarnings("unchecked")
  public <T> T putValue(String key, T value) {
    return (T) put(key, value);
  }

  /**
   * 放入业务生成的值
   *
   * @param key
   * @param value
   * @return
   */
  public <T> T putGen(String key, T value) {
    return putMulti("G-" + key, value);
  }

  /**
   * 获取业务生成的字段值
   *
   * @param key
   * @return
   */
  public <T> T getGen(String key) {
    return getMulti("G-" + key);
  }
}

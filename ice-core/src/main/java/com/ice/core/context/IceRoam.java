package com.ice.core.context;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zjn
 * based on ConcurrentHashMap extend
 * put/get return null while key/value is null (ignore key/value null)
 */
public class IceRoam extends ConcurrentHashMap<String, Object> implements Serializable {

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

    /*
     * use '.' split key achieve arrangement
     *
     * @param multiKey multiKey
     * @param value value
     */
    @SuppressWarnings("unchecked")
    public <T> T putMulti(String multiKey, Object value) {
        if (multiKey == null || value == null) {
            return null;
        }
        String[] keys = multiKey.split("\\.");
        if (keys.length == 1) {
            /*just one*/
            return (T) put(keys[0], value);
        }
        Map<String, Object> endMap = this;
        int i = 0;
        for (; i < keys.length - 1; i++) {
            endMap = (Map<String, Object>) endMap.computeIfAbsent(keys[i], k -> new IceRoam());
        }
        return (T) endMap.put(keys[i], value);
    }

    /*
     * use '.' split key to find arrangement value
     *
     * @param multiKey multiKey
     * @return value
     */
    @SuppressWarnings("unchecked")
    public <T> T getMulti(String multiKey) {
        if (multiKey == null) {
            return null;
        }
        String[] keys = multiKey.split("\\.");
        if (keys.length == 1) {
            /*只有一个*/
            return (T) get(keys[0]);
        }
        Map<String, Object> end = this;
        int i = 0;
        for (; i < keys.length - 1; i++) {
            end = (Map<String, Object>) end.get(keys[i]);
            if (end == null) {
                return null;
            }
        }
        return (T) end.get(keys[i]);
    }

    /*
     * Multi source find value value
     * prefix with '@' string directing to roam value
     *
     * @param union unionObj
     * @return value
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
    public <T> T putValue(String key, Object value) {
        return (T) put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String key) {
        return (T) get(key);
    }

    public <T> T getUnion(Object union, T defaultValue) {
        T res = getUnion(union);
        return res == null ? defaultValue : res;
    }

    public <T> T getValue(String key, T defaultValue) {
        T res = getValue(key);
        return res == null ? defaultValue : res;
    }

    public <T> T getMulti(String multiKey, T defaultValue) {
        T res = getMulti(multiKey);
        return res == null ? defaultValue : res;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        T res = (T) get(key);
        return res == null ? defaultValue : res;
    }

    @Override
    public Object put(String key, Object value) {
        if (key == null || value == null) {
            return null;
        }
        return super.put(key, value);
    }

    @Override
    public Object get(Object key) {
        if (key == null) {
            return null;
        }
        return super.get(key);
    }
}

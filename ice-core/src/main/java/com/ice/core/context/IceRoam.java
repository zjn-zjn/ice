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
    public <T> T putMulti(String multiKey, T value) {
        if (multiKey == null || value == null) {
            return null;
        }
        String[] keys = multiKey.split("\\.");
        if (keys.length == 1) {
            /*just one*/
            return (T) put(keys[0], value);
        }
        IceRoam end = this;
        IceRoam forward = this;
        int i = 0;
        for (; i < keys.length - 1; i++) {
            end = end.getValue(keys[i]);
            if (end == null) {
                int j = i;
                for (; j < keys.length - 1; j++) {
                    end = new IceRoam();
                    forward.put(keys[j], end);
                    forward = end;
                }
                i = j;
                break;
            } else {
                forward = end;
            }
        }
        if (end == null) {
            return null;
        }
        return (T) end.put(keys[i], value);
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
        IceRoam end = this;
        int i = 0;
        for (; i < keys.length - 1; i++) {
            end = (IceRoam) end.get(keys[i]);
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
    public <T> T putValue(String key, T value) {
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

    public Object get(String key, Object defaultValue) {
        Object res = get(key);
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

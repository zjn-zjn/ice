package com.ice.core.context;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zjn
 * based on HashMap extend
 */
public final class IceRoam extends ConcurrentHashMap<String, Object> implements Serializable {

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
        if (multiKey == null) {
            return null;
        }
        String[] keys = multiKey.split("\\.");
        if (keys.length == 1) {
            /*just one*/
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
                    endMap = new ConcurrentHashMap<>();
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

    /*
     * use '.' split key to find arrangement value
     *
     * @param multiKey multiKey
     * @return value
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
}

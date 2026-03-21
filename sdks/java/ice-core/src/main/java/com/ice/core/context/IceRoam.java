package com.ice.core.context;

import com.ice.common.utils.UUIDUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author waitmoon
 * based on ConcurrentHashMap extend
 * put null value will remove the key (ConcurrentHashMap does not support null values)
 * "_ice" key is reserved for IceMeta
 */
public class IceRoam extends ConcurrentHashMap<String, Object> {

    private static final String ICE_META_KEY = "_ice";

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

    public static IceRoam create() {
        IceRoam roam = new IceRoam();
        roam.putDirect(ICE_META_KEY, new IceMeta());
        return roam;
    }

    public static IceRoam create(String trace, long ts) {
        IceRoam roam = new IceRoam();
        IceMeta meta = new IceMeta();
        if (trace != null && !trace.isEmpty()) {
            meta.setTrace(trace);
        }
        if (ts > 0) {
            meta.setTs(ts);
        }
        roam.putDirect(ICE_META_KEY, meta);
        return roam;
    }

    public IceMeta getIceMeta() {
        return (IceMeta) super.get(ICE_META_KEY);
    }

    public long getIceId() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getId() : 0;
    }

    public String getIceScene() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getScene() : null;
    }

    public long getIceTs() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getTs() : 0;
    }

    public String getIceTrace() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getTrace() : null;
    }

    public StringBuilder getIceProcess() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getProcess() : null;
    }

    public byte getIceDebug() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getDebug() : 0;
    }

    public long getIceNid() {
        IceMeta meta = getIceMeta();
        return meta != null ? meta.getNid() : 0;
    }

    /**
     * Bypass "_ice" key protection for internal use
     */
    public Object putDirect(String key, Object value) {
        if (key == null) {
            return null;
        }
        if (value == null) {
            return super.remove(key);
        }
        return super.put(key, value);
    }

    /**
     * Shallow copy business data + copy IceMeta with fresh process StringBuilder
     */
    public IceRoam cloneRoam() {
        IceRoam clone = new IceRoam();
        for (Entry<String, Object> entry : this.entrySet()) {
            if (!ICE_META_KEY.equals(entry.getKey())) {
                clone.putDirect(entry.getKey(), entry.getValue());
            }
        }
        IceMeta meta = getIceMeta();
        if (meta != null) {
            clone.putDirect(ICE_META_KEY, new IceMeta(meta));
        }
        return clone;
    }

    /*
     * use '.' split key achieve arrangement
     *
     * @param deepKey deepKey
     * @param value value
     */
    @SuppressWarnings("unchecked")
    public <T> T putDeep(String deepKey, Object value) {
        if (deepKey == null) {
            return null;
        }
        String[] keys = deepKey.split("\\.");
        if (keys.length == 1) {
            /*just one*/
            return (T) put(keys[0], value);
        }
        Object end = this;
        for (int i = 0; i < keys.length - 1; i++) {
            if (end instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) end;
                end = map.computeIfAbsent(keys[i], k -> new IceRoam());
            } else if (end instanceof List) {
                try {
                    end = ((List<Object>) end).get(Integer.parseInt(keys[i]));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        String lastKey = keys[keys.length - 1];
        if (end instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) end;
            if (value == null) {
                return (T) map.remove(lastKey);
            }
            return (T) map.put(lastKey, value);
        } else if (end instanceof List) {
            try {
                List<Object> list = (List<Object>) end;
                int idx = Integer.parseInt(lastKey);
                Object old = list.get(idx);
                list.set(idx, value);
                return (T) old;
            } catch (NumberFormatException | IndexOutOfBoundsException e) {
                return null;
            }
        }
        return null;
    }

    /*
     * use '.' split key to find arrangement value
     *
     * @param deepKey deepKey
     * @return value
     */
    @SuppressWarnings("unchecked")
    public <T> T getDeep(String deepKey) {
        if (deepKey == null) {
            return null;
        }
        String[] keys = deepKey.split("\\.");
        if (keys.length == 1) {
            /*only one key*/
            return (T) get(keys[0]);
        }
        Object end = this;
        for (String key : keys) {
            if (end instanceof Map) {
                end = ((Map<String, Object>) end).get(key);
            } else if (end instanceof List) {
                try {
                    end = ((List<Object>) end).get(Integer.parseInt(key));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
            if (end == null) {
                return null;
            }
        }
        return (T) end;
    }

    /*
     * Multi source find value value
     * prefix with '@' string directing to roam value
     *
     * @param union unionObj
     * @return value
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(Object union) {
        if (union == null) {
            return null;
        }
        if (union instanceof String) {
            String key = (String) union;
            if (!(key).isEmpty() && (key).charAt(0) == '@') {
                return resolve(getDeep(key.substring(1)));
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

    public <T> T resolve(Object union, T defaultValue) {
        T res = resolve(union);
        return res == null ? defaultValue : res;
    }

    public <T> T getValue(String key, T defaultValue) {
        T res = getValue(key);
        return res == null ? defaultValue : res;
    }

    public <T> T getDeep(String deepKey, T defaultValue) {
        T res = getDeep(deepKey);
        return res == null ? defaultValue : res;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        T res = (T) get(key);
        return res == null ? defaultValue : res;
    }

    @Override
    public Object put(String key, Object value) {
        if (key == null) {
            return null;
        }
        if (ICE_META_KEY.equals(key)) {
            return super.get(ICE_META_KEY);
        }
        if (value == null) {
            return super.remove(key);
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

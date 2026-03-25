package com.ice.core.context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author waitmoon
 * based on ConcurrentHashMap extend
 * put null value will remove the key (ConcurrentHashMap does not support null values)
 */
public class IceRoam extends ConcurrentHashMap<String, Object> {

    private IceMeta meta;

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
        roam.meta = new IceMeta();
        return roam;
    }

    public static IceRoam create(String scene) {
        IceRoam roam = new IceRoam();
        roam.meta = new IceMeta(scene, 0, null);
        return roam;
    }

    public static IceRoam create(String trace, long ts) {
        IceRoam roam = new IceRoam();
        roam.meta = new IceMeta(null, ts, trace);
        return roam;
    }

    // ============ meta getters ============

    public IceMeta getMeta() {
        return meta;
    }

    public long getId() {
        return meta != null ? meta.getId() : 0;
    }

    public String getScene() {
        return meta != null ? meta.getScene() : null;
    }

    public long getTs() {
        return meta != null ? meta.getTs() : 0;
    }

    public String getTrace() {
        return meta != null ? meta.getTrace() : null;
    }

    public StringBuilder getProcess() {
        return meta != null ? meta.getProcess() : null;
    }

    public byte getDebug() {
        return meta != null ? meta.getDebug() : 0;
    }

    public long getNid() {
        return meta != null ? meta.getNid() : 0;
    }

    // ============ meta setters ============

    public void setId(long id) {
        if (meta != null) meta.setId(id);
    }

    public void setScene(String scene) {
        if (meta != null) meta.setScene(scene);
    }

    public void setTs(long ts) {
        if (meta != null) meta.setTs(ts);
    }

    public void setTrace(String trace) {
        if (meta != null) meta.setTrace(trace);
    }

    public void setDebug(byte debug) {
        if (meta != null) meta.setDebug(debug);
    }

    public void setNid(long nid) {
        if (meta != null) meta.setNid(nid);
    }

    /**
     * Shallow copy business data + clone meta with fresh process StringBuilder
     */
    public IceRoam cloneRoam() {
        IceRoam clone = new IceRoam();
        for (Entry<String, Object> entry : this.entrySet()) {
            clone.put(entry.getKey(), entry.getValue());
        }
        if (this.meta != null) {
            clone.meta = this.meta.cloneMeta();
        }
        return clone;
    }

    // ============ deep key operations ============

    /*
     * use '.' split key achieve arrangement
     */
    @SuppressWarnings("unchecked")
    public <T> T putDeep(String deepKey, Object value) {
        if (deepKey == null) {
            return null;
        }
        String[] keys = deepKey.split("\\.");
        if (keys.length == 1) {
            return (T) put(keys[0], value);
        }
        Object end = this;
        for (int i = 0; i < keys.length - 1; i++) {
            if (end instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) end;
                end = map.computeIfAbsent(keys[i], k -> new HashMap<>());
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
     */
    @SuppressWarnings("unchecked")
    public <T> T getDeep(String deepKey) {
        if (deepKey == null) {
            return null;
        }
        String[] keys = deepKey.split("\\.");
        if (keys.length == 1) {
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

    /**
     * Delete a top-level key from roam.
     */
    public Object del(String key) {
        if (key == null) {
            return null;
        }
        return super.remove(key);
    }

    /**
     * Delete a value using a dot-separated key path (e.g., "a.b.c").
     */
    @SuppressWarnings("unchecked")
    public Object delDeep(String deepKey) {
        if (deepKey == null) {
            return null;
        }
        String[] keys = deepKey.split("\\.");
        if (keys.length == 1) {
            return del(keys[0]);
        }
        Object end = this;
        for (int i = 0; i < keys.length - 1; i++) {
            if (end instanceof Map) {
                end = ((Map<String, Object>) end).get(keys[i]);
            } else if (end instanceof List) {
                try {
                    end = ((List<Object>) end).get(Integer.parseInt(keys[i]));
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
        String lastKey = keys[keys.length - 1];
        if (end instanceof Map) {
            return ((Map<String, Object>) end).remove(lastKey);
        }
        return null;
    }

    /*
     * Multi source find value
     * prefix with '@' string directing to roam value
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

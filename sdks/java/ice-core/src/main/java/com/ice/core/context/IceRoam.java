package com.ice.core.context;

import com.ice.common.utils.UUIDUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author waitmoon
 * based on ConcurrentHashMap extend
 * put null value will remove the key (ConcurrentHashMap does not support null values)
 * "_ice" key stores ice metadata as a plain Map
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
        roam.put(ICE_META_KEY, newMetaMap(null, 0, null));
        return roam;
    }

    public static IceRoam create(String scene) {
        IceRoam roam = new IceRoam();
        roam.put(ICE_META_KEY, newMetaMap(scene, 0, null));
        return roam;
    }

    public static IceRoam create(String trace, long ts) {
        IceRoam roam = new IceRoam();
        roam.put(ICE_META_KEY, newMetaMap(null, ts, trace));
        return roam;
    }

    private static Map<String, Object> newMetaMap(String scene, long ts, String trace) {
        Map<String, Object> ice = new HashMap<>();
        if (scene != null && !scene.isEmpty()) {
            ice.put("scene", scene);
        }
        ice.put("ts", ts > 0 ? ts : System.currentTimeMillis());
        ice.put("trace", (trace != null && !trace.isEmpty()) ? trace : UUIDUtils.generateAlphanumId(11));
        ice.put("process", new StringBuilder());
        return ice;
    }

    // ============ _ice convenience getters ============

    @SuppressWarnings("unchecked")
    public Map<String, Object> getMeta() {
        Object ice = super.get(ICE_META_KEY);
        return ice instanceof Map ? (Map<String, Object>) ice : null;
    }

    public long getId() {
        return getMetaLong("id");
    }

    public String getScene() {
        return getMetaString("scene");
    }

    public long getTs() {
        return getMetaLong("ts");
    }

    public String getTrace() {
        return getMetaString("trace");
    }

    public StringBuilder getProcess() {
        Map<String, Object> ice = getMeta();
        if (ice == null) return null;
        Object p = ice.get("process");
        return p instanceof StringBuilder ? (StringBuilder) p : null;
    }

    public byte getDebug() {
        Map<String, Object> ice = getMeta();
        if (ice == null) return 0;
        Object d = ice.get("debug");
        if (d instanceof Number) return ((Number) d).byteValue();
        return 0;
    }

    public long getNid() {
        return getMetaLong("nid");
    }

    // ============ _ice convenience setters ============

    public void setId(long id) {
        putMeta("id", id);
    }

    public void setScene(String scene) {
        putMeta("scene", scene);
    }

    public void setTs(long ts) {
        putMeta("ts", ts);
    }

    public void setTrace(String trace) {
        putMeta("trace", trace);
    }

    public void setDebug(byte debug) {
        putMeta("debug", debug);
    }

    public void setNid(long nid) {
        putMeta("nid", nid);
    }

    // ============ _ice internal helpers ============

    private long getMetaLong(String field) {
        Map<String, Object> ice = getMeta();
        if (ice == null) return 0;
        Object v = ice.get(field);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0;
    }

    private String getMetaString(String field) {
        Map<String, Object> ice = getMeta();
        if (ice == null) return null;
        Object v = ice.get(field);
        return v instanceof String ? (String) v : null;
    }

    @SuppressWarnings("unchecked")
    private void putMeta(String field, Object value) {
        Object ice = super.get(ICE_META_KEY);
        if (ice instanceof Map) {
            ((Map<String, Object>) ice).put(field, value);
        }
    }

    /**
     * Shallow copy business data + deep copy _ice map with fresh process StringBuilder
     */
    @SuppressWarnings("unchecked")
    public IceRoam cloneRoam() {
        IceRoam clone = new IceRoam();
        for (Entry<String, Object> entry : this.entrySet()) {
            if (ICE_META_KEY.equals(entry.getKey())) {
                Object ice = entry.getValue();
                if (ice instanceof Map) {
                    Map<String, Object> iceCopy = new HashMap<>((Map<String, Object>) ice);
                    iceCopy.put("process", new StringBuilder());
                    clone.put(ICE_META_KEY, iceCopy);
                }
            } else {
                clone.put(entry.getKey(), entry.getValue());
            }
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

package com.ice.core.context;

import com.ice.common.utils.UUIDUtils;

/**
 * @author waitmoon
 * Ice execution metadata.
 */
public class IceMeta {
    private long id;
    private String scene;
    private long nid;
    private long ts;
    private String trace;
    private byte debug;
    private StringBuilder process;

    public IceMeta() {
        this.ts = System.currentTimeMillis();
        this.trace = UUIDUtils.generateAlphanumId(11);
        this.process = new StringBuilder();
    }

    public IceMeta(String scene, long ts, String trace) {
        this.scene = scene;
        this.ts = ts > 0 ? ts : System.currentTimeMillis();
        this.trace = (trace != null && !trace.isEmpty()) ? trace : UUIDUtils.generateAlphanumId(11);
        this.process = new StringBuilder();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public long getNid() {
        return nid;
    }

    public void setNid(long nid) {
        this.nid = nid;
    }

    public long getTs() {
        return ts;
    }

    public void setTs(long ts) {
        this.ts = ts;
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public byte getDebug() {
        return debug;
    }

    public void setDebug(byte debug) {
        this.debug = debug;
    }

    public StringBuilder getProcess() {
        return process;
    }

    public IceMeta cloneMeta() {
        IceMeta copy = new IceMeta();
        copy.id = this.id;
        copy.scene = this.scene;
        copy.nid = this.nid;
        copy.ts = this.ts;
        copy.trace = this.trace;
        copy.debug = this.debug;
        copy.process = new StringBuilder();
        return copy;
    }
}

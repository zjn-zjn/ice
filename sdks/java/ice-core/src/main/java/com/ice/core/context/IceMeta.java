package com.ice.core.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ice.common.utils.UUIDUtils;
import lombok.Data;

import java.io.Serializable;

/**
 * @author waitmoon
 * Ice metadata stored under "_ice" key in IceRoam
 */
@Data
public final class IceMeta implements Serializable {

    private long id;

    private String scene;

    private long nid;

    private long ts;

    @JsonIgnore
    private String trace;

    private byte debug;

    private StringBuilder process;

    public IceMeta() {
        this.ts = System.currentTimeMillis();
        this.trace = UUIDUtils.generateAlphanumId(11);
        this.process = new StringBuilder();
    }

    public IceMeta(IceMeta other) {
        this.id = other.id;
        this.scene = other.scene;
        this.nid = other.nid;
        this.ts = other.ts;
        this.trace = other.trace;
        this.debug = other.debug;
        this.process = new StringBuilder();
    }
}

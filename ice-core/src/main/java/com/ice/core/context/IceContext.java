package com.ice.core.context;

import lombok.Data;
import lombok.ToString;

/**
 * @author zjn
 * Ice process context
 */
@Data
@ToString
public final class IceContext {

    /*
     * in ice process time (cxt init time)
     */
    private final long iceTime = System.currentTimeMillis();
    /*
     * process iceId
     */
    private long iceId;
    /*
     * user input
     */
    private IcePack pack;
    /*
     * current processing nodeId
     */
    private long currentId;
    /*
     * current processing node parentId
     */
    private long currentParentId;
    /*
     * current loop
     */
    private int currentLoop;
    /*
     * current processing node nextId
     */
    private long nextId;
    /*
     * node debug set true then process info
     */
    private StringBuilder processInfo = new StringBuilder();

    public IceContext(long iceId, IcePack pack) {
        this.iceId = iceId;
        this.pack = pack == null ? new IcePack() : pack;
    }
}

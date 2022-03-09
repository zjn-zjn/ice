package com.ice.core.context;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author zjn
 * Ice process context
 */
@Data
@ToString
public final class IceContext implements Serializable {

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
     * node debug set true then process info
     */
    private StringBuilder processInfo = new StringBuilder();

    public IceContext(long iceId, IcePack pack) {
        this.iceId = iceId;
        this.pack = pack == null ? new IcePack() : pack;
    }
}

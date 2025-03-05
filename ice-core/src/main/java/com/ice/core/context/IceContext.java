package com.ice.core.context;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 * @author waitmoon
 * Ice process context
 */
@Data
@ToString
@NoArgsConstructor
public final class IceContext implements Serializable {

    /*
     * in ice process time (ctx init time)
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

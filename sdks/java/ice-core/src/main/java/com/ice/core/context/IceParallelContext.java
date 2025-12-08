package com.ice.core.context;

import lombok.Data;

import java.io.Serializable;

/**
 * @author waitmoon
 */
@Data
public final class IceParallelContext implements Serializable {

    private volatile boolean isDone;

    private IceContext ctx;

    public IceParallelContext(IceContext ctx) {
        this.ctx = ctx;
    }
}

package com.ice.core.context;

import lombok.Data;

@Data
public final class IceParallelContext {

    private volatile boolean isDone;

    private IceContext cxt;

    public IceParallelContext(IceContext cxt) {
        this.cxt = cxt;
    }
}

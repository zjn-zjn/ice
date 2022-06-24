package com.ice.core.context;

import lombok.Data;

import java.io.Serializable;

@Data
public final class IceParallelContext implements Serializable {

    private volatile boolean isDone;

    private IceContext cxt;

    public IceParallelContext(IceContext cxt) {
        this.cxt = cxt;
    }
}

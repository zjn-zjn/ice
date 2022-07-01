package com.ice.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NodeException extends RuntimeException {
    private static final long serialVersionUID = 6944501264551776451L;
    private long parentId;
    private long nodeId;

    public NodeException(long nodeId, Throwable cause) {
        super(cause);
        this.nodeId = nodeId;
    }
}

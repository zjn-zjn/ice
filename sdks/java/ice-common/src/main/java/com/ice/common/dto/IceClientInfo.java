package com.ice.common.dto;

import com.ice.common.model.LeafNodeInfo;
import lombok.Data;

import java.util.List;

/**
 * Client info DTO.
 * Used for client to report its information to file system.
 *
 * @author waitmoon
 */
@Data
public final class IceClientInfo {

    /**
     * Client address (host/app/uniqueId).
     */
    private String address;

    /**
     * Application ID.
     */
    private Integer app;

    /**
     * Leaf node class information.
     */
    private List<LeafNodeInfo> leafNodes;

    /**
     * Last heartbeat timestamp in milliseconds.
     */
    private Long lastHeartbeat;

    /**
     * Client start timestamp in milliseconds.
     */
    private Long startTime;

    /**
     * Currently loaded version.
     */
    private Long loadedVersion;
}


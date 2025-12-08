package com.ice.common.dto;

import com.ice.common.model.LeafNodeInfo;
import lombok.Data;

import java.util.List;

/**
 * 客户端信息DTO
 * 用于客户端向文件系统上报自身信息
 *
 * @author waitmoon
 */
@Data
public final class IceClientInfo {

    /**
     * 客户端地址 (host:port:pid)
     */
    private String address;

    /**
     * 应用ID
     */
    private Integer app;

    /**
     * 叶子节点类信息
     */
    private List<LeafNodeInfo> leafNodes;

    /**
     * 最后心跳时间戳(毫秒)
     */
    private Long lastHeartbeat;

    /**
     * 客户端启动时间戳(毫秒)
     */
    private Long startTime;

    /**
     * 当前加载的版本号
     */
    private Long loadedVersion;
}


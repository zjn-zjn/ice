package com.ice.server.model;

import lombok.Data;

import java.util.Collection;
import java.util.List;

/**
 * @author zjn
 */
@Data
public class EditResult {
    private Integer code;
    private String msg;
    private Long nodeId;
    private Long linkId;
    private Long unLinkId;
    private List<Long> linkIds;
}

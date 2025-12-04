package com.ice.server.model;

import lombok.Data;

/**
 * @author waitmoon
 */
@Data
public class IceLeafClass {

    private String fullName;
    private String name;
    /**
     * 排序顺序，值越小越靠前
     */
    private int order;
}

package com.ice.server.model;

import lombok.Data;

/**
 * @author waitmoon
 */
@Data
public class IceLeafClass {

    private String fullName;
    private String name;
    private int count;

    public int sortNegativeCount() {
        return -count;
    }
}

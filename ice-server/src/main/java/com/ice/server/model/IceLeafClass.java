package com.ice.server.model;

import lombok.Data;

/**
 * @author zjn
 */
@Data
public class IceLeafClass {

    private String shortName;
    private String fullName;
    private int count;

    public int sortNegativeCount() {
        return -count;
    }
}

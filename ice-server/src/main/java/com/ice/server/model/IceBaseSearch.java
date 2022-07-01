package com.ice.server.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author waitmoon
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IceBaseSearch {

    private Integer app;
    private Long baseId;
    private String name;
    private String scene;
    private Integer pageNum;
    private Integer pageSize;
}

package com.ice.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zjn
 */
@Data
@NoArgsConstructor
public class IceEditNode {

    private Integer app;

    private Integer editType;

    private Long iceId;

    //operate nodeId add&edit need
    private Long selectId;

    private String name;

    private String confField;

    private Byte nodeType;

    private String confName;

    private String multiplexIds;

    private Byte timeType;

    private Long start;

    private Long end;

    private Boolean debug;

    private Long parentId;

    private Integer index;

    private Integer moveTo;

    private Long nextId;

    private Boolean inverse;

    public void setStart(Long start) {
        /*change all parameters normally to prevent the millisecond interception caused by the carry problem of MySQL datetime*/
        if (start != null) {
            start = start - start % 1000;
        }
        this.start = start;
    }

    public void setEnd(Long end) {
        /*change all parameters normally to prevent the millisecond interception caused by the carry problem of MySQL datetime*/
        if (end != null) {
            end = end - end % 1000;
        }
        this.end = end;
    }
}

package com.ice.node.common.flow;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 * 比大小比相等
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompareFlow extends BaseLeafRoamFlow {

    private Object value1;

    private Object value2;
    /*
     * 1判大(默认)
     * 0判等
     * -1判小
     */
    private int code = 0;

    @Override
    protected boolean doRoamFlow(IceRoam roam) {
        Comparable<Object> value1 = roam.getUnion(this.value1);
        if (value1 == null && code != 0) {
            return false;
        }
        Comparable<Object> value2 = roam.getUnion(this.value2);
        if (value2 == null) {
            return code == 0 && value1 == null;
        }
        if (value1 == null) {
            return false;
        }
        return value1.compareTo(value2) == code;
    }
}

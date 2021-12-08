package com.ice.test.flow;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 * 取出roam中的值比较大小
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScoreFlow2 extends BaseLeafRoamFlow {

    private Object value1;

    private Object value2;

    @Override
    protected boolean doRoamFlow(IceRoam roam) {
        Comparable<Object> value1 = roam.getUnion(this.value1);
        if (value1 == null) {
            return false;
        }
        Comparable<Object> value2 = roam.getUnion(this.value2);
        if (value2 == null) {
            return false;
        }
        return value1.compareTo(value2) >= 0;
    }
}

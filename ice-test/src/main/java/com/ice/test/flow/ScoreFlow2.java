package com.ice.test.flow;

import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceNode;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 * 取出roam中的值比较大小
 */
@Data
@EqualsAndHashCode(callSuper = true)
@IceNode(name = "name of ScoreFlow2", desc = "desc of ScoreFlow2")
public class ScoreFlow2 extends BaseLeafRoamFlow {

    private Object value1;
    @IceField(name = "name of value2", desc = "desc of value2")
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

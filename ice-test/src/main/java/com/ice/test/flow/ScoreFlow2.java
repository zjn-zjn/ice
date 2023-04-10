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
@IceNode(name = "比大小2号", desc = "比大小2号的描述")
public class ScoreFlow2 extends BaseLeafRoamFlow {

    @IceField(name = "比较值1", desc = "比较值1的描述")
    private Object value1;
    @IceField(name = "比较值2", desc = "比较值2的描述")
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

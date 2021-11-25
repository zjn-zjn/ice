package com.ice.test.flow;

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

    private Object key;

    private Object another;
    /*
     * 1判大(默认)
     * 0判等
     * -1判小
     */
    private int code = 1;

    /*
     * 叶子节点流程处理
     *
     * @param roam 传递roam
     */
    @Override
    protected boolean doRoamFlow(IceRoam roam) {
        Comparable<Object> keyValue = roam.getUnion(key);
        if (keyValue == null && code != 0) {
            return false;
        }
        Comparable<Object> anotherValue = roam.getUnion(another);
        if (anotherValue == null) {
            return code == 0 && keyValue == null;
        }
        if (keyValue == null) {
            return false;
        }
        return keyValue.compareTo(anotherValue) == code;
    }
}

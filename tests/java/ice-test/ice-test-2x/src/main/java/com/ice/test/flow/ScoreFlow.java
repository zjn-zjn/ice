package com.ice.test.flow;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.base.BaseLeafFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author waitmoon
 * 取出roam中的值比较大小
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScoreFlow extends BaseLeafFlow {

    private double score;

    private String key;

    /*
     * 叶子节点流程处理
     *
     * @param roam 传递roam
     */
    @Override
    protected boolean doFlow(IceRoam roam) {
        Object value = roam.getDeep(key);
        if (value == null) {
            return false;
        }
        double valueScore = Double.parseDouble(value.toString());
        return !(valueScore < score);
    }
}

package com.ice.test.flow;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Random;

/**
 * @author zjn
 * 取出roam中的值比较大小
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScoreFlow extends BaseLeafRoamFlow {

    private double score;

    private String key;

    /*
     * 叶子节点流程处理
     *
     * @param roam 传递roam
     */
    @Override
    protected boolean doRoamFlow(IceRoam roam) {
        try {
            Thread.sleep(new Random().nextInt(1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Object value = roam.getMulti(key);
        if (value == null) {
            return false;
        }
        double valueScore = Double.parseDouble(value.toString());
        return !(valueScore < score);
    }
}

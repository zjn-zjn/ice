package com.ice.test.flow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 * ice结合Aviator
 * exp 可供配置的表达式
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AviatorFlow extends BaseLeafRoamFlow {

    private String exp;

    private Expression compiledExpression;

    @Override
    protected boolean doRoamFlow(IceRoam roam) {
        return (boolean) compiledExpression.execute(roam);
    }
    public void setExp(String exp) {
        this.exp = exp;
        this.compiledExpression = AviatorEvaluator.compile(exp);
    }
}

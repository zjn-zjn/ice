package com.ice.test.flow;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.ice.common.enums.NodeRunStateEnum;
import com.ice.core.annotation.IceIgnore;
import com.ice.core.context.IceRoam;
import com.ice.core.leaf.base.BaseLeafFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * @author waitmoon
 * ice with Aviator
 * exp: aviator expression
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class AviatorFlow extends BaseLeafFlow {

    private String exp;

    @IceIgnore
    private Expression compiledExpression;

    @Override
    protected boolean doFlow(IceRoam roam) {
        return (boolean) compiledExpression.execute(roam);
    }

    @Override
    public void afterPropertiesSet() {
        if (exp != null) {
            this.compiledExpression = AviatorEvaluator.compile(exp, true);
        }
    }

    public NodeRunStateEnum errorHandle(IceRoam roam, Throwable t) {
        log.error("error occur id:{} e:", this.findIceNodeId(), t);
        return super.errorHandle(roam, t);
    }
}

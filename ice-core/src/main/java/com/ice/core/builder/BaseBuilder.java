package com.ice.core.builder;

import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseNode;
import lombok.Data;

/**
 * @author zjn
 */
@Data
public class BaseBuilder {

    private BaseNode node;

    public BaseBuilder(BaseNode node) {
        if (node.getIceTimeTypeEnum() == null) {
            node.setIceTimeTypeEnum(TimeTypeEnum.NONE);
        }
        node.setIceNodeDebug(true);
        this.node = node;
    }

    public BaseNode build() {
        return node;
    }

    public BaseBuilder forward(BaseNode forward) {
        this.node.setIceForward(forward);
        return this;
    }

    public BaseBuilder forward(BaseBuilder builder) {
        this.node.setIceForward(builder.build());
        return this;
    }

    public BaseBuilder start(long start) {
        this.node.setIceStart(start);
        return this;
    }

    public BaseBuilder end(long end) {
        this.node.setIceEnd(end);
        return this;
    }

    public BaseBuilder timeType(TimeTypeEnum typeEnum) {
        this.node.setIceTimeTypeEnum(typeEnum);
        return this;
    }

    public BaseBuilder inverse() {
        this.node.setIceInverse(true);
        return this;
    }

    public BaseBuilder debug() {
        this.node.setIceNodeDebug(true);
        return this;
    }
}
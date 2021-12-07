package com.ice.core.builder;

import com.ice.common.enums.ErrorHandleEnum;
import com.ice.common.enums.TimeTypeEnum;
import com.ice.core.base.BaseLeaf;
import com.ice.core.base.BaseNode;

/**
 * @author zjn
 */
public class LeafBuilder extends BaseBuilder {

    public LeafBuilder(BaseLeaf leaf) {
        super(leaf);
        if (leaf.getIceErrorHandleEnum() == null) {
            leaf.setIceErrorHandleEnum(ErrorHandleEnum.SHUT_DOWN);
        }
    }

    public static LeafBuilder leaf(BaseLeaf leaf) {
        return new LeafBuilder(leaf);
    }

    @Override
    public LeafBuilder forward(BaseNode forward) {
        return (LeafBuilder) super.forward(forward);
    }

    @Override
    public LeafBuilder forward(BaseBuilder builder) {
        return (LeafBuilder) super.forward(builder);
    }

    @Override
    public LeafBuilder start(long start) {
        return (LeafBuilder) super.start(start);
    }

    @Override
    public LeafBuilder end(long end) {
        return (LeafBuilder) super.end(end);
    }

    @Override
    public LeafBuilder timeType(TimeTypeEnum typeEnum) {
        return (LeafBuilder) super.timeType(typeEnum);
    }

    public LeafBuilder errorHandle(ErrorHandleEnum handleEnum) {
        ((BaseLeaf) this.getNode()).setIceErrorHandleEnum(handleEnum);
        return this;
    }
}
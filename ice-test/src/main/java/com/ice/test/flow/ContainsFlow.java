package com.ice.test.flow;

import com.ice.core.context.IceRoam;
import com.ice.core.leaf.roam.BaseLeafRoamFlow;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Collection;

/**
 * @author zjn
 * 判断key对应的值是否在set中
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContainsFlow extends BaseLeafRoamFlow {

    private Object value;
    /**
     * Collection
     * eg:Set[1,2]
     */
    private Object collection;

    @Override
    protected boolean doRoamFlow(IceRoam roam) {
        Collection<Object> collection = roam.getUnion(this.collection);
        if (collection == null || collection.isEmpty()) {
            return false;
        }
        return collection.contains(roam.getUnion(value));
    }
}

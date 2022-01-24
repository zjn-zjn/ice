package com.ice.core.base;

import com.ice.core.utils.IceLinkedList;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 * base relation node
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseRelation extends BaseNode {

    private IceLinkedList<BaseNode> children;

    /*
     * future exec default false
     */
    private boolean future;

    protected BaseRelation() {
        children = new IceLinkedList<>();
    }
}

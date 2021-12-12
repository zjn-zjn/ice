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

    /*
     * loop count
     * 1.default 0 not loop
     * 2.in and/any shutdown loop on false/true
     * 3.<0 used on and/any infinite loop shutdown with false/true
     */
    private int loop;

    protected BaseRelation() {
        children = new IceLinkedList<>();
    }
}

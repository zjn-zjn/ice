package com.ice.core.base;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ice.core.utils.IceLinkedList;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author waitmoon
 * base relation node
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseRelation extends BaseNode {

    @JsonIgnore
    private IceLinkedList<BaseNode> children;

    @JsonIgnore
    private List<Long> iceSonIds;

    protected BaseRelation() {
        children = new IceLinkedList<>();
        iceSonIds = new ArrayList<>();
    }
}

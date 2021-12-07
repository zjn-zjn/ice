package com.ice.core.base;

import com.ice.core.utils.IceLinkedList;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author zjn
 * 基础关系节点
 * 关系节点不能为叶子节点
 * 注意:开发时应避免与基础字段一致
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseRelation extends BaseNode {
    /*
     * 关系节点的子节点
     **/
    private IceLinkedList<BaseNode> children;

    /*
     * 并行 默认false
     */
    private boolean future;

    /*
     * 循环次数
     * 1.默认0 不循环
     * 2.在and/any下循环将在false/true时终止
     * 3.<0 是用在and/any下无限循环直至false/true终止
     */
    private int loop;

    protected BaseRelation() {
        children = new IceLinkedList<>();
    }
}

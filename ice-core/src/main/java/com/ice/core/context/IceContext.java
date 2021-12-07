package com.ice.core.context;

import lombok.Data;
import lombok.ToString;

/**
 * @author zjn
 * Ice执行上下文
 */
@Data
@ToString
public final class IceContext {

    /*
     * 进入ice开始执行的瞬间(cxt初始化瞬间)
     */
    private final long iceTime = System.currentTimeMillis();
    /*
     * 执行的iceId
     */
    private long iceId;
    /*
     * 请求内容
     */
    private IcePack pack;
    /*
     * 当前正在执行的节点ID
     */
    private long currentId;
    /*
     * 当前正在执行的节点的父节点ID
     */
    private long currentParentId;
    /*
     * 当前循环点
     */
    private int currentLoop;
    /*
     * 当前正在执行节点的后置节点ID
     */
    private long nextId;
    /*
     * debug为true的节点执行过程信息
     */
    private StringBuilder processInfo = new StringBuilder();

    public IceContext(long iceId, IcePack pack) {
        this.iceId = iceId;
        this.pack = pack == null ? new IcePack() : pack;
    }
}

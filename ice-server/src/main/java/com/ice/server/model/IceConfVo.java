package com.ice.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zjn
 */
@Data
@NoArgsConstructor
@Deprecated
public class IceConfVo {

    private Integer app;

    private Integer type;

    private Long iceId;

    /*
     * 操作节点ID 编辑和删除必传，新建根节点不传，新建子节点或forward必传
     */
    private Long operateNodeId;
    /*
     * 编辑字段 基础配置 名称 非必传
     */
    private String name;
    /*
     * 编辑字段 仅叶子节点编辑 非必传 默认{}
     */
    private String confField;
    /*
     * 新建字段  新建必传建的类型(除新建来自节点ID)
     */
    private Byte nodeType;
    /*
     * 新建字段 新建叶子必传(可用户输入自定义)
     */
    private String confName;
    /*
     * 新建字段 新建来自节点ID时必传 多个用","分隔
     */
    private String nodeId;
    /*
     * 编辑字段 基础配置 时间类型 非必传 默认1
     */
    private Byte timeType = 1;
    /*
     * 编辑字段 基础配置 开始时间 timeType=2,4,5,7必传
     */
    private Long start;
    /*
     * 编辑字段 基础配置 结束时间 timeType=3,4,6,7必传
     */
    private Long end;
    /*
     * 编辑字段 基础配置 非必传 默认true
     */
    private Boolean debug = true;
    /*
     * 父节点ID
     */
    private Long parentId;
    /*
     * 后置节点ID
     */
    private Long nextId;
    /*
     * 反转  默认false
     */
    private Boolean inverse = false;

    public void setStart(Long start) {
        /*防止mysql dateTime进位问题 截取掉毫秒*/
        if (start != null) {
            start = start - start % 1000;
        }
        this.start = start;
    }

    public void setEnd(Long end) {
        /*防止mysql dateTime进位问题 截取掉毫秒*/
        if (end != null) {
            end = end - end % 1000;
        }
        this.end = end;
    }
}

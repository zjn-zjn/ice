package com.ice.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ice-server配置属性
 *
 * @author waitmoon
 */
@Data
@ConfigurationProperties(prefix = "ice")
public class IceServerProperties {

    /**
     * 文件系统存储配置
     */
    private IceStorageProperties storage = new IceStorageProperties();

    /**
     * 客户端失活超时时间(秒)
     */
    private int clientTimeout = 60;

    /**
     * 版本文件保留数量
     */
    private int versionRetention = 1000;

    /**
     * 回收cron表达式
     * 默认每天凌晨3点执行
     */
    private String recycleCron = "0 0 3 * * ?";

    /**
     * 回收方式: hard-硬删除, soft-软删除
     */
    private String recycleWay = "soft";

    /**
     * 回收保护天数
     * 更新时间在此天数内的节点不会被回收，防止误删新创建的节点
     * 默认1天
     */
    private int recycleProtectDays = 1;

    @Data
    public static class IceStorageProperties {
        /**
         * 文件系统存储路径
         */
        private String path = "./ice-data";
    }
}

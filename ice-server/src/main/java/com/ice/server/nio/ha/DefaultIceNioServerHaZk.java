package com.ice.server.nio.ha;


import com.ice.core.utils.IceAddressUtils;
import com.ice.server.config.IceServerProperties;
import com.ice.server.nio.IceNioClientManager;
import com.ice.server.service.IceServerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
@ConditionalOnProperty("ice.ha.address")
@ConditionalOnClass({LeaderLatch.class, CuratorFramework.class})
public class DefaultIceNioServerHaZk implements IceNioServerHa {

    private LeaderLatch leaderLatch;

    private CuratorFramework client;

    public static volatile boolean leader = false;

    private final static String LATCH_PATH = "/com.waitmoon.ice.server";

    @Value("${server.port}")
    private int serverPort;

    @Resource
    private IceServerProperties properties;

    @Resource
    private IceServerService serverService;

    @Resource
    private IceNioClientManager iceNioClientManager;

    @Override
    public void register() throws Exception {
        client = CuratorFrameworkFactory.builder()
                .connectString(properties.getHa().getAddress())
                .retryPolicy(new ExponentialBackoffRetry(properties.getHa().getBaseSleepTimeMs(), properties.getHa().getMaxRetries(), properties.getHa().getMaxSleepMs()))
                .connectionTimeoutMs(properties.getHa().getConnectionTimeoutMs()).build();
        //first get the host from config to solve multiple network problem, then get address without 127.0.0.1
        String host = properties.getHa().getHost() == null ? IceAddressUtils.getAddress() : properties.getHa().getHost();
        if (!StringUtils.hasLength(host)) {
            throw new RuntimeException("ice server failed register zk, get host null");
        }
        //host:nio-port,host:web-port
        String id = host + ":" + properties.getPort() + "," + host + ":" + serverPort;
        log.info("server:" + id + " will register to zk for HA");
        leaderLatch = new LeaderLatch(client, LATCH_PATH, id, LeaderLatch.CloseMode.NOTIFY_LEADER);
        leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                leader = true;
                /*server becomes to leader refresh local cache from database*/
                serverService.refresh();
                log.info(id + " is server leader now");
            }

            @Override
            public void notLeader() {
                leader = false;
                log.info(id + " off server leader");
                serverService.cleanConfigCache();
                iceNioClientManager.cleanChannelCache();
            }
        });
        client.start();
        leaderLatch.start();
    }

    @Override
    public void destroy() throws IOException {
        if (leaderLatch != null) {
            leaderLatch.close();
        }
        if (client != null) {
            client.close();
        }
    }

    @Override
    public boolean isLeader() {
        return leader;
    }

    @Override
    public String getLeaderWebAddress() throws Exception {
        return leaderLatch == null ? null : leaderLatch.getLeader().getId().split(",")[1];
    }
}

package com.ice.core.client.ha;

import com.ice.common.constant.Constant;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.retry.RetryNTimes;

/**
 * @author waitmoon
 * server ha used zk
 */
public class IceServerHaZkDiscovery implements IceServerHaDiscovery {

    private static final String ZK_PREFIX = "zookeeper:";
    private final static String LATCH_PATH = "/com.waitmoon.ice.server";
    //zk curator framework
    private CuratorFramework zkClient;
    //zk leader latch to find server leader
    private LeaderLatch leaderLatch;
    //zk address
    private String zkAddress;

    private boolean support;
    //server leader
    private String serverLeaderAddress;

    @Override
    public boolean support() {
        return support;
    }

    @Override
    public void init(String server) {
        if (server.startsWith(ZK_PREFIX)) {
            support = true;
            //HA from zk
            zkAddress = server.substring(ZK_PREFIX.length());
        } else {
            support = false;
        }
    }

    @Override
    public String refreshServerLeaderAddress() throws Exception {
        if (!support) {
            return null;
        }
        if (zkClient == null) {
            zkClient = CuratorFrameworkFactory.builder()
                    .connectString(zkAddress)
                    .retryPolicy(new RetryNTimes(3, 2000))
                    .connectionTimeoutMs(5000).build();
        }
        if (leaderLatch == null) {
            leaderLatch = new LeaderLatch(zkClient, LATCH_PATH);
        }
        if (zkClient.getState() != CuratorFrameworkState.STARTED) {
            zkClient.start();
        }
        String serverLeader = leaderLatch.getLeader().getId();
        if (serverLeader == null || serverLeader.isEmpty()) {
            throw new RuntimeException("can not get ice server leader from zk:" + zkAddress);
        }
        serverLeaderAddress = serverLeader.split(Constant.REGEX_COMMA)[0];
        return serverLeaderAddress;
    }

    @Override
    public String getServerLeaderAddress() {
        return serverLeaderAddress;
    }

    @Override
    public void destroy() {
        if (support) {
            zkClient.close();
        }
    }
}
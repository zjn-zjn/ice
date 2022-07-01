package com.ice.server.nio;

import com.ice.core.client.IceNioModel;
import com.ice.core.client.NioOps;
import com.ice.core.client.NioType;
import com.ice.core.utils.IceNioUtils;
import com.ice.server.service.IceServerService;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author waitmoon
 */
@Slf4j
public class IceNioServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

    public static Map<String, Object> lockMap = new ConcurrentHashMap<>();

    public static Map<String, IceNioModel> resultMap = new ConcurrentHashMap<>();

    private final IceServerService serverService;


    public IceNioServerHandler(IceServerService serverService) {
        this.serverService = serverService;
    }

    /*
     * unregister while channel inactive
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        IceNioClientManager.unregister(channel);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        Channel channel = ctx.channel();
        IceNioModel nioModel = IceNioUtils.readNioModel(buf);
        if (nioModel != null && nioModel.getType() != null && nioModel.getOps() != null) {
            switch (nioModel.getType()) {
                case REQ:
                    if (nioModel.getOps() == NioOps.INIT) {
                        IceNioModel response = new IceNioModel();
                        response.setType(NioType.RSP);
                        response.setOps(NioOps.INIT);
                        response.setInitDto(serverService.getInitConfig(nioModel.getApp()));
                        IceNioUtils.writeNioModel(ctx, response);
                        IceNioClientManager.register(nioModel.getApp(), channel, nioModel.getAddress(), nioModel.getNodeInfos());
                    } else if (nioModel.getOps() == NioOps.SLAP) {
                        IceNioClientManager.register(nioModel.getApp(), channel, nioModel.getAddress(), null);
                    }
                    break;
                case RSP:
                    //handle the response of client
                    String id = nioModel.getId();
                    if (id != null) {
                        Object lock = lockMap.get(id);
                        if (lock != null) {
                            synchronized (lock) {
                                if (lockMap.containsKey(id)) {
                                    resultMap.put(id, nioModel);
                                    lock.notifyAll();
                                }
                            }
                        }
                    }
                    break;
            }
        }
    }

    /*
     * if there is no read request for readerIdleTime, close the client
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (IdleState.READER_IDLE.equals((event.state()))) {
                IceNioClientManager.unregister(ctx.channel());
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("ice server channel error:", cause);
        ctx.close();
    }
}
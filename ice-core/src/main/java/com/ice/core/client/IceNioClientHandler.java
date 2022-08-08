package com.ice.core.client;

import com.ice.common.model.IceShowConf;
import com.ice.common.model.Pair;
import com.ice.core.context.IceContext;
import com.ice.core.utils.IceNioUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author waitmoon
 */
@Slf4j
public class IceNioClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final IceNioClient iceNioClient;

    private final int app;

    public IceNioClientHandler(int app, IceNioClient iceNioClient) {
        this.app = app;
        this.iceNioClient = iceNioClient;
    }

    /*
     * ice client init when channel active
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        //init client
        IceNioModel initRequest = new IceNioModel();
        initRequest.setOps(NioOps.INIT);
        initRequest.setType(NioType.REQ);
        initRequest.setApp(app);
        initRequest.setAddress(iceNioClient.getIceAddress());
        //leaf node class
        initRequest.setLeafNodes(iceNioClient.getLeafNodes());
        IceNioUtils.writeNioModel(ctx, initRequest);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        IceNioModel nioModel = IceNioUtils.readNioModel(buf);
        if (nioModel != null && nioModel.getType() != null && nioModel.getOps() != null) {
            switch (nioModel.getType()) {
                case REQ:
                    iceNioClient.waitStarted(); //provide service after client started
                    IceNioModel response = new IceNioModel();
                    response.setType(NioType.RSP);
                    response.setId(nioModel.getId());
                    response.setOps(nioModel.getOps());
                    switch (nioModel.getOps()) {
                        case CLAZZ_CHECK:
                            Pair<Integer, String> checkResult = IceNioClientService.confClazzCheck(nioModel.getClazz(), nioModel.getNodeType(), iceNioClient.getIceAddress());
                            response.setClazzCheck(checkResult);
                            break;
                        case UPDATE:
                            List<String> errors = IceNioClientService.update(nioModel.getUpdateDto());
                            response.setUpdateErrors(errors);
                            break;
                        case SHOW_CONF:
                            IceShowConf conf = IceNioClientService.getShowConf(nioModel.getConfId(), iceNioClient.getIceAddress());
                            response.setShowConf(conf);
                            break;
                        case MOCK:
                            List<IceContext> mockResults = IceNioClientService.mock(nioModel.getPack());
                            response.setMockResults(mockResults);
                            break;
                    }
                    IceNioUtils.writeNioModel(ctx, response);
                    break;
                case RSP:
                    if (nioModel.getOps() == NioOps.INIT) {
                        iceNioClient.initDataReady(nioModel.getInitDto());
                    }
                    break;
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws InterruptedException {
        if (!iceNioClient.isDestroy()) {
            log.info("ice client is broken reconnecting...");
            iceNioClient.reconnect();
        }
    }

    /*
     * if there is no read request, send for 5 seconds
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (IdleState.READER_IDLE.equals((event.state()))) {
                IceNioModel nioModel = new IceNioModel();
                nioModel.setOps(NioOps.SLAP);
                nioModel.setType(NioType.REQ);
                nioModel.setAddress(iceNioClient.getIceAddress());
                nioModel.setApp(app);
                IceNioUtils.writeNioModel(ctx, nioModel);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("ice client channel error:", cause);
        ctx.close();
    }
}
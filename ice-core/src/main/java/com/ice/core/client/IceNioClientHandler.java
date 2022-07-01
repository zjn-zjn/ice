package com.ice.core.client;

import com.ice.common.enums.NodeTypeEnum;
import com.ice.common.model.IceShowConf;
import com.ice.common.model.NodeInfo;
import com.ice.core.annotation.IceField;
import com.ice.core.annotation.IceNode;
import com.ice.core.base.BaseLeaf;
import com.ice.core.context.IceContext;
import com.ice.core.leaf.base.BaseLeafFlow;
import com.ice.core.leaf.base.BaseLeafNone;
import com.ice.core.leaf.base.BaseLeafResult;
import com.ice.core.utils.IceNioUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author waitmoon
 */
@Slf4j
public class IceNioClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final IceNioClient iceNioClient;

    private final int app;

    public static volatile boolean init = false;

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
        initRequest.setAddress(iceNioClient.getAddress());
        //leaf node class
        initRequest.setNodeInfos(getLeafTypeClassMap(iceNioClient.getScanPackages()));
        IceNioUtils.writeNioModel(ctx, initRequest);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        IceNioModel nioModel = IceNioUtils.readNioModel(buf);
        if (nioModel != null && nioModel.getType() != null && nioModel.getOps() != null) {
            switch (nioModel.getType()) {
                case REQ:
                    IceNioModel response = new IceNioModel();
                    response.setType(NioType.RSP);
                    response.setId(nioModel.getId());
                    response.setOps(nioModel.getOps());
                    switch (nioModel.getOps()) {
                        case UPDATE:
                            List<String> errors = IceNioClientService.update(nioModel.getUpdateDto());
                            response.setUpdateErrors(errors);
                            break;
                        case SHOW_CONF:
                            IceShowConf conf = IceNioClientService.getShowConf(nioModel.getConfId(), iceNioClient.getAddress());
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
                        log.info("ice client init app:{} address:{}", app, iceNioClient.getAddress());
                        IceUpdate.update(nioModel.getInitDto());
                        init = true;
                        log.info("ice client init iceEnd success app:{} address:{}", app, iceNioClient.getAddress());
                    }
                    break;
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws InterruptedException {
        log.info("ice client is broken reconnecting...");
        iceNioClient.reconnect();
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
                nioModel.setAddress(iceNioClient.getAddress());
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

    private static List<NodeInfo> getLeafTypeClassMap(Set<String> scanPackages) {
        if (scanPackages == null || scanPackages.isEmpty()) {
            //empty scan return null
            return null;
        }
        Reflections reflections = new Reflections(scanPackages.toArray());
        Set<Class<? extends BaseLeaf>> leafClasses = reflections.getSubTypesOf(BaseLeaf.class);
        if (leafClasses == null || leafClasses.isEmpty()) {
            return null;
        }
        List<NodeInfo> nodeInfos = new ArrayList<>(leafClasses.size());
        for (Class<? extends BaseLeaf> leafClass : leafClasses) {
            if (!Modifier.isAbstract(leafClass.getModifiers())) { //exclude abstract nodes
                NodeInfo nodeInfo = new NodeInfo();
                nodeInfo.setClazz(leafClass.getName());
                IceNode nodeAnnotation = leafClass.getAnnotation(IceNode.class);
                if (nodeAnnotation != null) {
                    nodeInfo.setName(nodeAnnotation.name());
                    nodeInfo.setDesc(nodeAnnotation.desc());
                }
                Field[] leafFields = leafClass.getDeclaredFields();
                List<NodeInfo.FieldInfo> annotationFields = new ArrayList<>(leafFields.length);
                for (Field field : leafFields) {
                    IceField fieldAnnotation = field.getAnnotation(IceField.class);
                    if (fieldAnnotation != null) {
                        NodeInfo.FieldInfo fieldInfo = new NodeInfo.FieldInfo();
                        fieldInfo.setField(field.getName());
                        fieldInfo.setName(fieldAnnotation.name());
                        fieldInfo.setDesc(fieldAnnotation.desc());
                        annotationFields.add(fieldInfo);
                    }
                }
                if (!annotationFields.isEmpty()) {
                    nodeInfo.setFields(annotationFields);
                }
                if (BaseLeafFlow.class.isAssignableFrom(leafClass)) {
                    nodeInfo.setType(NodeTypeEnum.LEAF_FLOW.getType());
                    nodeInfos.add(nodeInfo);
                    continue;
                }
                if (BaseLeafResult.class.isAssignableFrom(leafClass)) {
                    nodeInfo.setType(NodeTypeEnum.LEAF_RESULT.getType());
                    nodeInfos.add(nodeInfo);
                    continue;
                }
                if (BaseLeafNone.class.isAssignableFrom(leafClass)) {
                    nodeInfo.setType(NodeTypeEnum.LEAF_NONE.getType());
                    nodeInfos.add(nodeInfo);
                }
            }
        }
        return nodeInfos;
    }
}
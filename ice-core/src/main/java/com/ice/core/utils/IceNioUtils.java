package com.ice.core.utils;

import com.alibaba.fastjson.JSON;
import com.ice.core.client.IceNioModel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * @author zjn
 * ice nio operate
 */
@Slf4j
public final class IceNioUtils {

    public static IceNioModel getNioModel(ByteBuf buf) {
        IceNioModel model;
        try {
            model = JSON.parseObject(getNioModelJson(buf), IceNioModel.class);
            return model;
        } catch (Exception e) {
            log.warn("ice nio error please check data", e);
        }
        return null;
    }

    public static void writeNioModel(ChannelHandlerContext ctx, IceNioModel nioModel) {
        //write nio model to server/client
        writeNioModel(ctx.channel(), nioModel);
    }

    public static void writeNioModel(Channel channel, IceNioModel nioModel) {
        //write nio model to server/client
        byte[] bytes = JSON.toJSONString(nioModel).getBytes(StandardCharsets.UTF_8);
        ByteBuf buf = Unpooled.buffer(bytes.length);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        channel.writeAndFlush(buf);
    }

    public static String getNioModelJson(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

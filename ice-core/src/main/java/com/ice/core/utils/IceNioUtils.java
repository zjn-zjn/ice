package com.ice.core.utils;

import com.alibaba.fastjson.JSON;
import com.ice.core.nio.IceNioModel;
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
        return JSON.parseObject(getNioModelJson(buf), IceNioModel.class);
    }

    public static void writeNioModel(ChannelHandlerContext ctx, IceNioModel nioModel) {
        //write nio model to server/client
        byte[] req = JSON.toJSONString(nioModel).getBytes(StandardCharsets.UTF_8);
        ByteBuf message = Unpooled.buffer(req.length);
        message.writeInt(req.length);
        message.writeBytes(req);
        ctx.writeAndFlush(message);
    }

    public static void writeNioModel(Channel channel, IceNioModel nioModel) {
        //write nio model to server/client
        byte[] req = JSON.toJSONString(nioModel).getBytes(StandardCharsets.UTF_8);
        ByteBuf message = Unpooled.buffer(req.length);
        message.writeInt(req.length);
        message.writeBytes(req);
        channel.writeAndFlush(message);
    }

    public static String getNioModelJson(ByteBuf buf) {
        byte[] req = new byte[buf.readableBytes()];
        buf.readBytes(req);
        return new String(req, StandardCharsets.UTF_8);
    }
}

package com.ice.core.utils;

import com.alibaba.fastjson.JSON;
import com.ice.core.nio.IceNioModel;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 * @author zjn
 * ice nio operate
 */
@Slf4j
public final class IceNioUtils {

    private static final int bufferSize = 1024;
    //nio data header length
    private static final int headLen = 4;
    //16M, size bigger than this may dirty data
    private static final int maxSize = 16 * 1024 * 1024;

    public static IceNioModel getNioModel(SocketChannel sc) throws IOException {
        String json = getNioModelJson(sc);
        if (json != null) {
            return JSON.parseObject(json, IceNioModel.class);
        }
        return null;
    }

    public static void writeNioModel(SocketChannel sc, IceNioModel nioModel) throws IOException {
        //write nio model to server/client
        writeNioModelBytes(sc, JSON.toJSONBytes(nioModel));
    }

    private static void writeNioModelBytes(SocketChannel sc, byte[] jsonBytes) throws IOException {
        int len = jsonBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(headLen + len);
        buffer.put(intToBytes(len));
        buffer.put(jsonBytes);
        buffer.flip();
        sc.write(buffer);
        buffer.clear();
    }

    public static byte[] intToBytes(int value) {
        byte[] result = new byte[4];
        // from high to low
        result[0] = (byte) ((value >> 24) & 0xFF);
        result[1] = (byte) ((value >> 16) & 0xFF);
        result[2] = (byte) ((value >> 8) & 0xFF);
        result[3] = (byte) (value & 0xFF);
        return result;
    }

    public static synchronized String getNioModelJson(SocketChannel sc) throws IOException {
        ByteBuffer headBuffer = ByteBuffer.allocate(headLen);
        int headRead;
        while (headBuffer.hasRemaining()) {
            //get header
            headRead = sc.read(headBuffer);
            if (headRead == -1) {
                log.warn("Close channel {}", sc.getRemoteAddress());
                sc.close();
                return null;
            }
        }
        headBuffer.flip();
        //get body len from header
        int bodyLen = headBuffer.getInt();
        headBuffer.clear();
        if (bodyLen > maxSize) {
            log.error("size too big! or something went wrong, please contact with waitmoon!");
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(Math.min(bufferSize, bodyLen));
        int bufferCount = bodyLen / bufferSize;
        int tailSize = bodyLen % bufferSize;
        int read;
        StringBuilder sb = new StringBuilder(bodyLen);
        if (bufferCount > 0) {
            for (int i = 0; i < bufferCount; i++) {
                while (buffer.hasRemaining()) {
                    read = sc.read(buffer);
                    if (read == -1) {
                        log.warn("Close channel {}", sc.getRemoteAddress());
                        sc.close();
                        return null;
                    }
                }
                buffer.flip();
                sb.append(new String(buffer.array(), 0, bufferSize, StandardCharsets.UTF_8));
                buffer.clear();
            }
        }
        if (tailSize > 0) {
            buffer.limit(tailSize);
            while (buffer.hasRemaining()) {
                read = sc.read(buffer);
                if (read == -1) {
                    log.warn("Close channel {}", sc.getRemoteAddress());
                    sc.close();
                    return null;
                }
            }
            buffer.flip();
            sb.append(new String(buffer.array(), 0, tailSize, StandardCharsets.UTF_8));
            buffer.clear();
        }
        return sb.toString();
    }
}

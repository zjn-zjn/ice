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

    private static final byte[] sign = {119, 97, 105, 116, 109, 111, 111, 110};

    private static final int signInt = ByteBuffer.wrap(sign).getInt();
    //after 20 times continue read zero in sc,return null
    private static final int zeroCount = 15;

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
        ByteBuffer buffer = ByteBuffer.allocate(sign.length + headLen + len);
        buffer.put(sign);
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

    public static String getNioModelJson(SocketChannel sc) throws IOException {
        ByteBuffer signBuffer = ByteBuffer.allocate(sign.length);
        int signRead = sc.read(signBuffer);
        if (signRead == -1) {
            signBuffer.clear();
            log.warn("Close channel {}", sc.getRemoteAddress());
            sc.close();
            return null;
        }
        signBuffer.flip();
        if (signRead < sign.length || signBuffer.getInt() != signInt) {
            signBuffer.clear();
            log.warn("dirty data from {}", sc.getRemoteAddress());
            sc.close();
            return null;
        }
        signBuffer.clear();
        ByteBuffer headBuffer = ByteBuffer.allocate(headLen);
        int headRead = sc.read(headBuffer);
        if (headRead == -1) {
            headBuffer.clear();
            log.warn("Close channel {}", sc.getRemoteAddress());
            sc.close();
            return null;
        }
        headBuffer.flip();
        int bodyLen;
        if (headRead < headLen || (bodyLen = headBuffer.getInt()) > maxSize || bodyLen < 2) {
            headBuffer.clear();
            log.warn("dirty/bigger data from {}", sc.getRemoteAddress());
            sc.close();
            return null;
        }
        headBuffer.clear();
        ByteBuffer buffer = ByteBuffer.allocate(Math.min(bufferSize, bodyLen));
        int bufferCount = bodyLen / bufferSize;
        int tailSize = bodyLen % bufferSize;
        int read;
        StringBuilder sb = new StringBuilder(bodyLen);
        if (bufferCount > 0) {
            for (int i = 0; i < bufferCount; i++) {
                int zeroNum = 0;
                while (buffer.hasRemaining()) {
                    read = sc.read(buffer);
                    if (read == -1) {
                        buffer.clear();
                        log.warn("Close channel {}", sc.getRemoteAddress());
                        sc.close();
                        return null;
                    }
                    if (read == 0) {
                        if (++zeroNum < zeroCount) {
                            log.info("zero num:{}", zeroNum);
                            continue;
                        }
                        buffer.clear();
                        log.warn("zero continue {} times in {}, return null", zeroNum, sc.getRemoteAddress());
                        return null;
                    } else {
                        zeroNum = 0;
                    }
                }
                buffer.flip();
                sb.append(new String(buffer.array(), 0, bufferSize, StandardCharsets.UTF_8));
                buffer.clear();
            }
        }
        if (tailSize > 0) {
            buffer.limit(tailSize);
            int zeroNum = 0;
            while (buffer.hasRemaining()) {
                read = sc.read(buffer);
                if (read == -1) {
                    buffer.clear();
                    log.warn("Close channel {}", sc.getRemoteAddress());
                    sc.close();
                    return null;
                }
                if (read == 0) {
                    if (++zeroNum < zeroCount) {
                        log.info("zero num:{}", zeroNum);
                        continue;
                    }
                    buffer.clear();
                    log.warn("zero continue {} times in {}, return null", zeroNum, sc.getRemoteAddress());
                    return null;
                } else {
                    zeroNum = 0;
                }
            }
            buffer.flip();
            sb.append(new String(buffer.array(), 0, tailSize, StandardCharsets.UTF_8));
            buffer.clear();
        }
        return sb.toString();
    }
}

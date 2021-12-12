package com.ice.common.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author zjn
 */
public final class UUIDUtils {

    /*
     * 64
     */
    private static final char[] DIGITS64 = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
            'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l',
            'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
            '9', '-', '_'};

    private UUIDUtils() {
    }

    /*
     * 21-22 UUID
     */
    public static String generateMost22UUID() {

        byte[] randomBytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(randomBytes);

        randomBytes[6] &= 0x0f;
        randomBytes[6] |= 0x40;
        randomBytes[8] &= 0x3f;
        randomBytes[8] |= 0x80;
        long msb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (randomBytes[i] & 0xff);
        }
        long lsb = 0;
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (randomBytes[i] & 0xff);
        }

        char[] buf = new char[22];
        int charPos = 22;
        int radix = 64;
        long mask = radix - 1;
        do {
            charPos--;
            buf[charPos] = DIGITS64[(int) (msb & mask)];
            msb >>>= 6;
        } while (msb != 0);

        do {
            charPos--;
            buf[charPos] = DIGITS64[(int) (lsb & mask)];
            lsb >>>= 6;
        } while (lsb != 0);
        return new String(buf, charPos, 22 - charPos);
    }

    /*
     * without "-" UUID
     */
    public static String generateUUID() {
        byte[] randomBytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(randomBytes);

        randomBytes[6] &= 0x0f;
        randomBytes[6] |= 0x40;
        randomBytes[8] &= 0x3f;
        randomBytes[8] |= 0x80;
        long msb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (randomBytes[i] & 0xff);
        }
        long lsb = 0;
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (randomBytes[i] & 0xff);
        }
        return (digits(msb >> 32, 8) + digits(msb >> 16, 4) + digits(msb, 4) + digits(lsb >> 48, 4) + digits(lsb, 12));
    }

    private static String digits(long val, int digits) {
        long hi = 1L << ((long) digits << 2);
        return Long.toHexString(hi | (val & (hi - 1)));
    }

    /*
     * 22 UUID
     */
    public static String generateUUID22() {
        byte[] randomBytes = new byte[16];
        ThreadLocalRandom.current().nextBytes(randomBytes);

        randomBytes[6] &= 0x0f;
        randomBytes[6] |= 0x40;
        randomBytes[8] &= 0x3f;
        randomBytes[8] |= 0x80;
        long msb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (randomBytes[i] & 0xff);
        }
        long lsb = 0;
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (randomBytes[i] & 0xff);
        }
        char[] out = new char[24];
        int bit = 0;
        int bt1 = 8;
        int bt2 = 8;

        for (int idx = 0; bit < 16; bit += 3, idx += 4) {
            int offsetm = 64 - ((bit + 3) << 3);

            int mask;
            if (bt1 > 3) {
                mask = (1 << 8 * 3) - 1;
            } else if (bt1 >= 0) {
                mask = (1 << 8 * bt1) - 1;
                bt2 -= 3 - bt1;
            } else {
                mask = (1 << 8 * (Math.min(bt2, 3))) - 1;
                bt2 -= 3;
            }
            int tmp = 0;
            if (bt1 > 0) {
                bt1 -= 3;
                tmp = (int) ((offsetm < 0) ? msb : (msb >>> offsetm) & mask);
                if (bt1 < 0) {
                    tmp <<= Math.abs(offsetm);
                    mask = (1 << 8 * Math.abs(bt1)) - 1;
                }
            }
            if (offsetm < 0) {
                int offsetl = 64 + offsetm;
                tmp |= ((offsetl < 0) ? lsb : (lsb >>> offsetl)) & mask;
            }

            if (bit == 15) {
                out[idx + 3] = DIGITS64[63];
                out[idx + 2] = DIGITS64[63];
                tmp <<= 4;
            } else {
                out[idx + 3] = DIGITS64[tmp & 0x3f];
                tmp >>= 6;
                out[idx + 2] = DIGITS64[tmp & 0x3f];
                tmp >>= 6;
            }
            out[idx + 1] = DIGITS64[tmp & 0x3f];
            tmp >>= 6;
            out[idx] = DIGITS64[tmp & 0x3f];
        }
        return new String(out, 0, 22);
    }
}
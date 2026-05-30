package com.wereen.competitionplatform.util;

import org.bouncycastle.crypto.digests.SM3Digest;

import java.nio.charset.StandardCharsets;

/**
 * SM3 国密哈希工具类
 *
 * <p>基于 BouncyCastle (bcprov-jdk15on) 实现 SM3 摘要算法，
 * BouncyCastle 已通过 FISCO BCOS Java SDK 作为传递依赖引入。
 */
public final class SM3HashUtil {

    private SM3HashUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 对字节数组计算 SM3 哈希，返回十六进制字符串（64 个小写十六进制字符）。
     *
     * @param data 原始字节数据，不能为 null
     * @return 32 字节 SM3 摘要的十六进制表示
     */
    public static String hashHex(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        SM3Digest digest = new SM3Digest();
        digest.update(data, 0, data.length);
        byte[] hash = new byte[digest.getDigestSize()]; // 32 bytes
        digest.doFinal(hash, 0);
        return bytesToHex(hash);
    }

    /**
     * 对 UTF-8 字符串计算 SM3 哈希，返回十六进制字符串（64 个小写十六进制字符）。
     *
     * @param data 原始字符串，不能为 null
     * @return 32 字节 SM3 摘要的十六进制表示
     */
    public static String hashHex(String data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return hashHex(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将字节数组转为小写十六进制字符串。
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}

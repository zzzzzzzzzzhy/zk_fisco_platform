package com.wereen.competitionplatform.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Merkle Tree 工具类
 * 用于生成防篡改的数据指纹
 */
public class MerkleTreeUtil {

    /**
     * 计算Merkle Root
     *
     * @param dataList 数据列表（如榜单记录、奖金记录等）
     * @return Merkle Root哈希值
     */
    public static String calculateMerkleRoot(List<String> dataList) {
        if (dataList == null || dataList.isEmpty()) {
            return "";
        }

        // 1. 对每条数据计算哈希，生成叶子节点
        List<String> hashes = new ArrayList<>();
        for (String data : dataList) {
            hashes.add(hash(data));
        }

        // 2. 自底向上构建Merkle Tree
        while (hashes.size() > 1) {
            List<String> newLevel = new ArrayList<>();

            // 两两配对计算父节点哈希
            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left; // 奇数个节点时，最后一个自己配对
                String parentHash = hash(left + right);
                newLevel.add(parentHash);
            }

            hashes = newLevel;
        }

        // 3. 返回根哈希
        return hashes.get(0);
    }

    /**
     * 计算SHA256哈希
     */
    private static String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算哈希失败", e);
        }
    }

    /**
     * 验证数据是否在Merkle Tree中
     * (简化版，实际应使用Merkle Proof)
     */
    public static boolean verify(List<String> dataList, String merkleRoot) {
        String calculatedRoot = calculateMerkleRoot(dataList);
        return calculatedRoot.equals(merkleRoot);
    }
}

package com.rain.zhixuecommon.utils;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * SHA256加盐迭代密码加密工具类（生产环境可用）
 * 特性：随机盐值+多次迭代+不可逆哈希
 */

@Slf4j
public class SHA256EncryptUtil {
    // 加密算法固定为SHA-256
    private static final String ALGORITHM = "SHA-256";
    // 随机盐值长度：16字节（推荐，可改为32字节增强安全性）
    private static final int SALT_LENGTH = 16;
    // 哈希迭代次数：10000次（推荐，10000-100000之间，值越大越安全，性能消耗略高）
    private static final int ITERATION_COUNT = 10000;
    // 字符编码：固定UTF-8，避免密码/盐值乱码
    private static final String CHARSET = "UTF-8";

    /**
     * 生成随机盐值（字节数组）
     * @return 16字节的随机盐值
     */
    public static byte[] generateSalt() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    /**
     * 字节数组转16进制字符串（用于存储盐值和密文）
     * @param bytes 待转换的字节数组
     * @return 16进制字符串（无空格/横线，纯字符）
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 16进制字符串转字节数组（验证密码时解析存储的盐值）
     * @param hex 16进制字符串
     * @return 对应的字节数组
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    /**
     * 核心方法：加盐+迭代实现SHA256哈希
     * @param rawPassword 原始明文密码
     * @param salt 盐值（字节数组）
     * @return 哈希后的字节数组
     * @throws NoSuchAlgorithmException 算法不存在异常（理论上不会出现）
     */
    private static byte[] sha256Hash(String rawPassword, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(ALGORITHM);
        // 第一步：将盐值传入消息摘要器
        md.update(salt);
        // 第二步：将原始密码转字节数组，传入摘要器
        byte[] passwordBytes = rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] hash = md.digest(passwordBytes);

        // 第三步：多次迭代哈希，增强安全性
        for (int i = 0; i < ITERATION_COUNT - 1; i++) {
            md.reset();
            hash = md.digest(hash);
        }
        return hash;
    }

    /**
     * 对外提供：密码加密方法（生成盐值+加密，返回「盐值$密文」格式字符串）
     * 格式说明：盐值和密文用$分隔，方便后续拆分验证
     * @param rawPassword 原始明文密码
     * @return 加密结果：盐值(16进制)$密文(16进制)
     * @throws NoSuchAlgorithmException
     */
    public static String encrypt(String rawPassword) throws NoSuchAlgorithmException {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = generateSalt(); // 生成随机盐值
        byte[] hashBytes = sha256Hash(rawPassword.trim(), salt); // 加盐迭代哈希
        String saltHex = bytesToHex(salt); // 盐值转16进制
        String hashHex = bytesToHex(hashBytes); // 密文转16进制
        return saltHex + "$" + hashHex; // 拼接返回，方便存储
    }

    /**
     * 对外提供：密码验证方法（无需解密，重新哈希对比）
     * @param rawPassword 用户输入的原始明文密码
     * @param encryptStr 数据库中存储的加密字符串（盐值$密文）
     * @return true-密码正确，false-密码错误
     * @throws NoSuchAlgorithmException
     */
    public static boolean verify(String rawPassword, String encryptStr) throws NoSuchAlgorithmException {
        if (rawPassword == null || encryptStr == null || !encryptStr.contains("$")) {
            return false;
        }
        // 拆分存储的盐值和密文
        String[] parts = encryptStr.split("\\$");
        if (parts.length != 2) {
            return false;
        }
        String saltHex = parts[0];
        String targetHashHex = parts[1];
        // 盐值转字节数组，用相同盐值重新哈希用户输入的密码
        byte[] salt = hexToBytes(saltHex);
        byte[] currentHashBytes = sha256Hash(rawPassword.trim(), salt);
        String currentHashHex = bytesToHex(currentHashBytes);
        // 对比两次密文是否一致
        //log.info("验证密码"+currentHashHex+"\n"+targetHashHex);
        return currentHashHex.equals(targetHashHex);
    }

}


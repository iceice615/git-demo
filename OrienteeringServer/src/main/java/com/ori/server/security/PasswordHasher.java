package com.ori.server.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 口令哈希工具：PBKDF2-HMAC-SHA256，只依赖 JDK，不引第三方库。
 *
 * 存储格式：pbkdf2$迭代次数$Base64(盐)$Base64(摘要)
 * 说明：历史数据里若还是明文口令（不含 pbkdf2$ 前缀），登录校验会兜底比对一次，
 *       校验通过后立即改写成哈希，实现"无感迁移"。
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String PREFIX = "pbkdf2$";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 口令强度下限：6 位起步，过长截断防止内存膨胀 */
    public static final int MIN_LEN = 6;
    public static final int MAX_LEN = 64;

    private PasswordHasher() {
    }

    /** 生成哈希 */
    public static String hash(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] digest = pbkdf2(rawPassword, salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(digest);
    }

    /** 校验口令（恒定时间比较，避免时序侧信道） */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isEmpty()) {
            return false;
        }
        if (!isHashed(stored)) {
            // 历史明文口令的兼容比对
            return MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    stored.getBytes(StandardCharsets.UTF_8));
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(rawPassword, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否已是哈希存储（用于判断要不要升级） */
    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_BITS);
            byte[] out = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            spec.clearPassword();
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希失败", e);
        }
    }

    /** 自检：单元测试或启动时可调用，确认哈希与校验闭环正常 */
    public static boolean selfTest() {
        String h = hash("ori-selftest");
        boolean ok = matches("ori-selftest", h) && !matches("wrong", h);
        Arrays.fill(h.toCharArray(), '\0');
        return ok;
    }
}

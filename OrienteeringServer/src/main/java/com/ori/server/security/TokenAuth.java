package com.ori.server.security;

import com.ori.server.entity.User;
import com.ori.server.repo.UserRepo;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * 会话令牌管理：登录签发、请求校验、过期回收。
 * 令牌用 32 字节随机数（Base64Url），不可预测；默认 7 天过期，单端登录（新登录挤掉旧令牌）。
 */
@Component
public class TokenAuth {

    /** 请求属性名：鉴权通过后把当前用户 id 挂在 request 上 */
    public static final String ATTR_UID = "currentUid";

    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepo userRepo;

    public TokenAuth(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    /** 签发新令牌（会作废该用户此前的令牌） */
    public String issue(User user) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        user.token = token;
        user.tokenExpires = System.currentTimeMillis() + TTL_MS;
        userRepo.save(user);
        return token;
    }

    /** 解析令牌，无效/过期返回 null（过期时顺手清理库里的令牌） */
    public Optional<User> resolve(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            return Optional.empty();
        }
        Optional<User> found = userRepo.findByToken(token);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        User u = found.get();
        if (u.tokenExpires == null || u.tokenExpires < System.currentTimeMillis()) {
            u.token = null;
            u.tokenExpires = null;
            userRepo.save(u);
            return Optional.empty();
        }
        return Optional.of(u);
    }

    /** 登出：作废令牌 */
    public void revoke(User user) {
        user.token = null;
        user.tokenExpires = null;
        userRepo.save(user);
    }

    public static long ttlMillis() {
        return TTL_MS;
    }
}

package com.ori.server.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：同一用户名 10 分钟内失败 5 次即锁定 10 分钟，
 * 防止脚本撞库。内存实现，重启即清零，够课设/小规模使用。
 */
@Component
public class LoginGuard {

    private static final int MAX_FAILS = 5;
    private static final long WINDOW_MS = 10L * 60 * 1000;
    private static final long LOCK_MS = 10L * 60 * 1000;

    private static final class Counter {
        int fails;
        long windowStart;
        long lockedUntil;
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** 是否处于锁定状态 */
    public boolean isLocked(String key) {
        Counter c = counters.get(key);
        return c != null && c.lockedUntil > System.currentTimeMillis();
    }

    /** 剩余锁定秒数（未锁定返回 0） */
    public long lockSecondsLeft(String key) {
        Counter c = counters.get(key);
        if (c == null || c.lockedUntil <= System.currentTimeMillis()) {
            return 0;
        }
        return (c.lockedUntil - System.currentTimeMillis()) / 1000 + 1;
    }

    /** 记一次失败，达到阈值则锁定 */
    public void onFailure(String key) {
        long now = System.currentTimeMillis();
        Counter c = counters.computeIfAbsent(key, k -> new Counter());
        if (c.windowStart == 0 || now - c.windowStart > WINDOW_MS) {
            c.fails = 0;
            c.windowStart = now;
        }
        c.fails++;
        if (c.fails >= MAX_FAILS) {
            c.lockedUntil = now + LOCK_MS;
            c.fails = 0;
            c.windowStart = now;
        }
    }

    /** 登录成功：清空计数 */
    public void onSuccess(String key) {
        counters.remove(key);
    }
}

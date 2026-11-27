package com.example.orienteeringassistant.db;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 登录状态管理：SharedPreferences 记住当前用户与访问令牌。
 *
 * 隐私说明：这里只保存服务端签发的令牌，不保存密码。
 * 令牌随机、可过期、可在服务端作废，即使被读取也无法反推账号口令。
 */
public class SessionManager {

    private static final String PREF_NAME = "session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_NAME = "name";
    private static final String KEY_CLAZZ = "clazz";

    /** 令牌键名，ApiClient 也读取同一个键，保持单点定义 */
    public static final String KEY_TOKEN = "token";
    public static final String PREF = PREF_NAME;

    private final SharedPreferences sp;

    public SessionManager(Context context) {
        sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 登录成功：写入用户信息与令牌 */
    public void login(long userId, String username, String token) {
        sp.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_TOKEN, token == null ? "" : token)
                .apply();
    }

    /** 读取令牌（未登录返回空串） */
    public String getToken() {
        return sp.getString(KEY_TOKEN, "");
    }

    public boolean isLoggedIn() {
        return sp.getLong(KEY_USER_ID, -1) != -1 && !getToken().isEmpty();
    }

    public long getUserId() {
        return sp.getLong(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return sp.getString(KEY_USERNAME, "");
    }

    public String getName() {
        return sp.getString(KEY_NAME, "");
    }

    public String getClazz() {
        return sp.getString(KEY_CLAZZ, "");
    }

    /** 登录/资料更新后保存姓名与班级 */
    public void setProfile(String name, String clazz) {
        sp.edit().putString(KEY_NAME, name == null ? "" : name)
                .putString(KEY_CLAZZ, clazz == null ? "" : clazz)
                .apply();
    }

    /** 登出：清空用户信息与令牌 */
    public void logout() {
        sp.edit().clear().apply();
    }
}

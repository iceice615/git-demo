package com.example.orienteeringassistant.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 云端接口客户端（HttpURLConnection + org.json，零第三方依赖）
 *
 * 所有方法都是【同步】的，会阻塞线程 ——
 * 调用时必须放在子线程里，例如：
 *   new Thread(() -> {
 *       JSONObject resp = ApiClient.post(ctx, "/api/login", body);
 *       runOnUiThread(() -> { ... 更新界面 ... });
 *   }).start();
 *
 * 鉴权：除登录/注册外，所有请求自动带上 Authorization: Bearer <token>，
 * 令牌从 SharedPreferences("session") 读取，与 SessionManager 共用同一个键。
 * 服务器地址可在登录页修改，默认 http://192.168.1.100:8090
 */
public class ApiClient {

    private static final String PREFS = "session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_URL = "base_url";

    private static final String DEFAULT_URL = "http://192.168.1.100:8090";

    /** 登录态失效（HTTP 401），调用方应提示重新登录 */
    public static class SessionExpiredException extends Exception {
        public SessionExpiredException() {
            super("登录已过期，请重新登录");
        }
    }

    public static String getServerUrl(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String url = sp.getString(KEY_URL, "");
        if (url.isEmpty()) {
            // 兼容旧版本：地址曾存在独立的 server_config 里，读到后迁移过来
            String legacy = context.getSharedPreferences("server_config", Context.MODE_PRIVATE)
                    .getString(KEY_URL, "");
            if (!legacy.isEmpty()) {
                sp.edit().putString(KEY_URL, legacy).apply();
                return legacy;
            }
            return DEFAULT_URL;
        }
        return url;
    }

    /** 保存服务器地址；只接受 http/https，杜绝 file:// 之类的协议注入 */
    public static void setServerUrl(Context context, String url) {
        String u = url == null ? "" : url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_URL, u).apply();
    }

    public static JSONObject get(Context context, String path) throws Exception {
        return request(context, "GET", path, null);
    }

    public static JSONObject post(Context context, String path, JSONObject body) throws Exception {
        return request(context, "POST", path, body);
    }

    public static JSONObject delete(Context context, String path) throws Exception {
        return request(context, "DELETE", path, null);
    }

    private static JSONObject request(Context context, String method, String path, JSONObject body)
            throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(getServerUrl(context) + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Accept", "application/json");

            // 自动附加令牌（登录/注册时没有令牌，自然不带）
            SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String token = sp.getString(KEY_TOKEN, "");
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }

            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = conn.getResponseCode();
            if (code == 401) {
                throw new SessionExpiredException();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    code >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                    StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            String text = sb.toString();
            if (code >= 400) {
                throw new Exception("服务器错误 " + code);
            }
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

package com.example.orienteeringassistant;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.SessionManager;
import com.example.orienteeringassistant.util.ApiClient;

import org.json.JSONObject;

/**
 * 登录页：APP 启动入口
 * 账号体系在云端服务器，换手机也能登录
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        // 已经登录过，跳过登录页
        if (session.isLoggedIn()) {
            goMain();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoRegister = findViewById(R.id.tvGoRegister);
        TextView tvServer = findViewById(R.id.tvServer);

        tvServer.setText("服务器：" + ApiClient.getServerUrl(this));
        tvServer.setOnClickListener(v -> showServerDialog());

        btnLogin.setOnClickListener(v -> doLogin());
        tvGoRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    /** 修改服务器地址 */
    private void showServerDialog() {
        final EditText input = new EditText(this);
        input.setText(ApiClient.getServerUrl(this));
        input.setHint("http://IP:8090");
        new AlertDialog.Builder(this)
                .setTitle("服务器地址")
                .setMessage("填写后端服务的地址")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(url)) {
                        ApiClient.setServerUrl(this, url);
                        Toast.makeText(this, "已保存：" + ApiClient.getServerUrl(this),
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            toast("请输入用户名和密码");
            return;
        }
        toast("登录中…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                JSONObject resp = ApiClient.post(this, "/api/login", body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        session.login(resp.optLong("id"), resp.optString("username"),
                                resp.optString("token"));
                        session.setProfile(resp.optString("name"), resp.optString("clazz"));
                        toast("登录成功，欢迎回来！");
                        goMain();
                    } else {
                        toast(resp.optString("msg", "登录失败"));
                    }
                });
            } catch (ApiClient.SessionExpiredException e) {
                runOnUiThread(() -> {
                    session.logout();
                    toast("登录已过期，请重新登录");
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        toast("无法连接服务器：" + e.getMessage() + "\n请检查网络或点击下方服务器地址"));
            }
        }).start();
    }

    private void goMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

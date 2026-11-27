package com.example.orienteeringassistant;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.util.ApiClient;

import org.json.JSONObject;

/**
 * 注册页（账号存云端服务器）
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private EditText etPassword2;
    private EditText etName;
    private EditText etClazz;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etPassword2 = findViewById(R.id.etPassword2);
        etName = findViewById(R.id.etRealName);
        etClazz = findViewById(R.id.etClazz);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String password2 = etPassword2.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String clazz = etClazz.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            toast("用户名和密码不能为空");
            return;
        }
        if (!username.matches("[A-Za-z0-9_]{3,20}")) {
            toast("用户名需为 3-20 位字母、数字或下划线");
            return;
        }
        if (password.length() < 6 || password.length() > 64) {
            toast("密码长度需在 6-64 位之间");
            return;
        }
        if (!password.equals(password2)) {
            toast("两次输入的密码不一致");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                body.put("name", name);
                body.put("clazz", clazz);
                JSONObject resp = ApiClient.post(this, "/api/register", body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        toast("注册成功，请登录");
                        finish(); // 返回登录页
                    } else {
                        toast(resp.optString("msg", "注册失败"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("无法连接服务器：" + e.getMessage()));
            }
        }).start();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

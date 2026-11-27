package com.example.orienteeringassistant;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.util.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 参赛成员管理（赛事发起者专用，像建群一样）
 * - 按用户名添加成员（对方需先注册）
 * - 长按成员移除
 * - 查看全员成绩
 */
public class MemberManageActivity extends AppCompatActivity {

    private long cloudId;
    private JSONArray members = new JSONArray();
    private final List<String> display = new ArrayList<>();
    private ListView listView;
    private EditText etUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_manage);

        cloudId = getIntent().getLongExtra("cloud_id", -1);
        String compName = getIntent().getStringExtra("comp_name");

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText((compName == null ? "赛事" : compName) + " · 成员管理");

        etUsername = findViewById(R.id.etUsername);
        listView = findViewById(R.id.lvMembers);
        listView.setEmptyView(findViewById(R.id.tvEmpty));
        Button btnAdd = findViewById(R.id.btnAddMember);
        Button btnResults = findViewById(R.id.btnAllResults);

        btnAdd.setOnClickListener(v -> addMember());
        btnResults.setOnClickListener(v -> {
            Intent intent = new Intent(this, MemberResultsActivity.class);
            intent.putExtra("cloud_id", cloudId);
            intent.putExtra("comp_name", compName);
            startActivity(intent);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            try {
                JSONObject m = members.getJSONObject(position);
                confirmRemove(m.optLong("userId"), m.optString("username"));
            } catch (Exception e) {
                toast("数据异常");
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMembers();
    }

    private void loadMembers() {
        new Thread(() -> {
            try {
                JSONObject resp = ApiClient.get(this,
                        "/api/competitions/" + cloudId + "/members");
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        members = resp.optJSONArray("data");
                        refreshList();
                    } else {
                        toast(resp.optString("msg", "加载失败"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("无法连接服务器：" + e.getMessage()));
            }
        }).start();
    }

    private void refreshList() {
        display.clear();
        try {
            for (int i = 0; i < members.length(); i++) {
                JSONObject m = members.getJSONObject(i);
                String name = m.optString("name");
                String clazz = m.optString("clazz");
                String team = m.optString("teamName");
                StringBuilder sb = new StringBuilder("👤 ");
                sb.append(name.isEmpty() ? m.optString("username") : name);
                if (!clazz.isEmpty()) {
                    sb.append("（").append(clazz).append("）");
                }
                sb.append("\n     学号 ").append(m.optString("username"));
                if (!team.isEmpty()) {
                    sb.append("  ｜  🚩 ").append(team);
                } else {
                    sb.append("  ｜  未分队");
                }
                display.add(sb.toString());
            }
        } catch (Exception ignored) {
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    private void addMember() {
        String username = etUsername.getText().toString().trim();
        if (TextUtils.isEmpty(username)) {
            toast("请输入要添加的用户名");
            return;
        }
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("username", username);
                JSONObject resp = ApiClient.post(this,
                        "/api/competitions/" + cloudId + "/members", body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        toast("已添加成员：" + resp.optString("username"));
                        etUsername.setText("");
                        loadMembers();
                    } else {
                        toast(resp.optString("msg", "添加失败"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("无法连接服务器：" + e.getMessage()));
            }
        }).start();
    }

    private void confirmRemove(long userId, String username) {
        new AlertDialog.Builder(this)
                .setTitle("移除成员")
                .setMessage("把「" + username + "」移出本赛事？\n（其历史成绩仍保留）")
                .setPositiveButton("移除", (d, w) -> removeMember(userId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void removeMember(long userId) {
        new Thread(() -> {
            try {
                JSONObject resp = ApiClient.delete(this,
                        "/api/competitions/" + cloudId + "/members?userId=" + userId);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        toast("已移除");
                        loadMembers();
                    } else {
                        toast(resp.optString("msg", "移除失败"));
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

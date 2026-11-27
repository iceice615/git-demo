package com.example.orienteeringassistant;

import android.content.Context;
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

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.db.SessionManager;
import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.util.ApiClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

/**
 * APP 主页：底部双 Tab（微信式）
 *  - 首页：开始比赛 / 赛事管理 / 运动训练
 *  - 我的：个人信息卡（姓名班级可编辑上传）、我的成绩、我参加过的比赛、退出登录
 */
public class MainActivity extends AppCompatActivity {

    private SessionManager session;
    private Button btnRace;
    private RaceRecord ongoing;
    private TextView tvWelcome;
    private TextView tvProfileName;
    private TextView tvProfileClazz;
    private TextView tvProfileUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        session = new SessionManager(this);

        // ===== 双 Tab =====
        View pageHome = findViewById(R.id.pageHome);
        View pageMe = findViewById(R.id.pageMe);
        BottomNavigationView nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tab_home) {
                pageHome.setVisibility(View.VISIBLE);
                pageMe.setVisibility(View.GONE);
            } else if (id == R.id.tab_me) {
                pageHome.setVisibility(View.GONE);
                pageMe.setVisibility(View.VISIBLE);
                refreshProfile();
            }
            return true;
        });

        // ===== 首页 =====
        tvWelcome = findViewById(R.id.tvWelcome);
        btnRace = findViewById(R.id.btnRace);
        Button btnCompetitions = findViewById(R.id.btnCompetitions);
        Button btnTraining = findViewById(R.id.btnTraining);

        btnCompetitions.setOnClickListener(v ->
                startActivity(new Intent(this, CompetitionListActivity.class)));

        Button btnJoinByKey = findViewById(R.id.btnJoinByKey);
        btnJoinByKey.setOnClickListener(v -> showJoinDialog());

        btnTraining.setOnClickListener(v ->
                startActivity(new Intent(this, TrainingActivity.class)));

        // 开始/继续比赛
        btnRace.setOnClickListener(v -> {
            if (ongoing != null) {
                Intent intent = new Intent(this, RaceActivity.class);
                intent.putExtra("competition_id", ongoing.getCompetitionId());
                startActivity(intent);
            } else {
                startActivity(new Intent(this, RaceSelectActivity.class));
            }
        });

        // ===== 我的 =====
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileClazz = findViewById(R.id.tvProfileClazz);
        tvProfileUser = findViewById(R.id.tvProfileUser);
        Button btnEditProfile = findViewById(R.id.btnEditProfile);
        Button btnRecords = findViewById(R.id.btnRecords);
        Button btnJoined = findViewById(R.id.btnJoined);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnEditProfile.setOnClickListener(v -> showEditProfileDialog());

        // 我的成绩
        btnRecords.setOnClickListener(v ->
                startActivity(new Intent(this, RecordsActivity.class)));

        // 我参加过的比赛
        btnJoined.setOnClickListener(v ->
                startActivity(new Intent(this, JoinedListActivity.class)));

        // 退出登录
        btnLogout.setOnClickListener(v -> {
            // 先通知服务端作废令牌（失败也不影响本地退出）
            final Context ctx = this;
            new Thread(() -> {
                try {
                    ApiClient.post(ctx, "/api/logout", new org.json.JSONObject());
                } catch (Exception ignored) {
                }
            }).start();
            session.logout();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查是否有比赛还在进行中（退出界面、杀进程后重进都能继续）
        ongoing = DBHelper.getInstance(this).getOngoingRaceAny(session.getUserId());
        if (ongoing != null) {
            btnRace.setText("继续比赛（进行中："
                    + (ongoing.getCompetitionName() == null ? "赛事" : ongoing.getCompetitionName())
                    + "）");
        } else {
            btnRace.setText("开始比赛（扫码打卡）");
        }
        refreshProfile();
    }

    /** 刷新"我的"页个人信息卡与首页欢迎语 */
    private void refreshProfile() {
        String name = session.getName();
        String clazz = session.getClazz();
        tvWelcome.setText("你好，" + (name.isEmpty() ? session.getUsername() : name));
        tvProfileName.setText(name.isEmpty() ? "未设置姓名" : name);
        tvProfileClazz.setText("班级：" + (clazz.isEmpty() ? "未填写" : clazz));
        tvProfileUser.setText("学号：" + session.getUsername());
    }

    /** 编辑姓名/班级并上传云端（发起者管理成员时能看到） */
    private void showEditProfileDialog() {
        View view = createDialogView();
        EditText etName = view.findViewById(R.id.etDialogName);
        EditText etClazz = view.findViewById(R.id.etDialogClazz);
        etName.setText(session.getName());
        etClazz.setText(session.getClazz());

        new AlertDialog.Builder(this)
                .setTitle("个人信息")
                .setMessage("填写真实姓名与班级，方便赛事发起者管理")
                .setView(view)
                .setPositiveButton("保存上传", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String clazz = etClazz.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        toast("姓名不能为空");
                        return;
                    }
                    saveProfile(name, clazz);
                })
                .setNegativeButton("取消", null)
                .setCancelable(false)
                .show();
    }

    private View createDialogView() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        EditText etName = new EditText(this);
        etName.setId(R.id.etDialogName);
        etName.setHint("姓名（如：张三）");
        etName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(etName);

        EditText etClazz = new EditText(this);
        etClazz.setId(R.id.etDialogClazz);
        etClazz.setHint("班级（如：计算机241）");
        etClazz.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        layout.addView(etClazz);
        return layout;
    }

    private void saveProfile(String name, String clazz) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("clazz", clazz);
                JSONObject resp = ApiClient.post(this,
                        "/api/users/" + session.getUserId(), body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        session.setProfile(name, clazz);
                        refreshProfile();
                        toast("已保存并同步到云端 ✅");
                    } else {
                        toast(resp.optString("msg", "保存失败"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("无法连接服务器：" + e.getMessage()));
            }
        }).start();
    }

    /** 输入 6 位加入码自助参赛（发起者发布赛事后生成的 id_key） */
    private void showJoinDialog() {
        EditText input = new EditText(this);
        input.setHint("输入 6 位加入码");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(6)});
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(this);
        wrapper.setPadding(pad, pad / 2, pad, 0);
        wrapper.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("加入赛事")
                .setMessage("向赛事发起者索要 6 位加入码，输入后即可参赛（无需发起者逐个拉人）")
                .setView(wrapper)
                .setPositiveButton("加入", (d, w) -> {
                    String key = input.getText().toString().trim();
                    if (key.length() != 6) {
                        toast("加入码是 6 位数字");
                        return;
                    }
                    joinByKey(key);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void joinByKey(String key) {
        toast("正在加入…");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("userId", session.getUserId());
                body.put("key", key);
                JSONObject resp = ApiClient.post(this, "/api/competitions/join", body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        String name = resp.optString("name", "赛事");
                        toast("✅ 已加入「" + name + "」");
                        // 打开"我参加过的比赛"，点一下就能下载赛道开跑
                        startActivity(new Intent(this, JoinedListActivity.class));
                    } else {
                        toast(resp.optString("msg", "加入失败，请核对加入码"));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("无法连接服务器：" + e.getMessage()));
            }
        }).start();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

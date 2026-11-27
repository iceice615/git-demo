package com.example.orienteeringassistant;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.db.SessionManager;
import com.example.orienteeringassistant.model.Checkpoint;
import com.example.orienteeringassistant.model.Competition;
import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.util.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 赛事创建/编辑表单页（每种模式独立界面，只显示该模式相关的字段）
 *
 * - 个人赛：名称 + 预约时间 + 成员 + 组别
 * - 积分赛：+ 进行时间 + 有效时间 + 每分钟扣分
 * - 团队赛：+ 进行时间（成员队伍在参赛时自选）
 * - 接力赛：+ 进行时间
 * - 组别：一次创建多个并行赛事（男子甲乙丙丁/女子甲乙丙丁…），
 *   每组是独立赛事、独立加入码、打卡互不冲突
 */
public class CompetitionFormActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";

    /** 进行时间下拉：小时 -> 分钟 */
    private static final int[] DURATION_MINUTES = {60, 180, 360, 720, 1440};
    private static final String[] DURATION_LABELS =
            {"1 小时", "3 小时", "6 小时", "12 小时", "24 小时"};
    private static final String[] PRESET_GROUPS =
            {"男子甲组", "男子乙组", "男子丙组", "男子丁组",
                    "女子甲组", "女子乙组", "女子丙组", "女子丁组"};

    private DBHelper db;
    private SessionManager session;
    private long editId = -1;
    private Competition editing;
    private String mode;

    private TextView tvMode;
    private EditText etName;
    private TextView tvDurationLabel;
    private Spinner spDuration;
    private LinearLayout scoreSection;
    private EditText etValidMinutes;
    private EditText etPenalty;
    private TextView tvStartTime;
    private TextView tvEndTime;
    private EditText etDate;
    private EditText etDesc;
    private EditText etMember;
    private ListView lvMembers;
    private EditText etGroup;
    private ListView lvGroups;

    private long startTime = 0;
    private long endTime = 0;

    /** 待拉入的成员（学号）。未发布赛事存在本地，发布时一起上传 */
    private final List<String> pendingMembers = new ArrayList<>();
    /** 组别列表 */
    private final List<String> groups = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_competition_form);

        db = DBHelper.getInstance(this);
        session = new SessionManager(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = "个人赛";
        }

        tvMode = findViewById(R.id.tvMode);
        etName = findViewById(R.id.etName);
        tvDurationLabel = findViewById(R.id.tvDurationLabel);
        spDuration = findViewById(R.id.spDuration);
        scoreSection = findViewById(R.id.scoreSection);
        etValidMinutes = findViewById(R.id.etValidMinutes);
        etPenalty = findViewById(R.id.etPenalty);
        tvStartTime = findViewById(R.id.tvStartTime);
        tvEndTime = findViewById(R.id.tvEndTime);
        etDate = findViewById(R.id.etDate);
        etDesc = findViewById(R.id.etDesc);
        etMember = findViewById(R.id.etMember);
        lvMembers = findViewById(R.id.lvMembers);
        etGroup = findViewById(R.id.etGroup);
        lvGroups = findViewById(R.id.lvGroups);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnAddMember = findViewById(R.id.btnAddMember);
        Button btnClearSchedule = findViewById(R.id.btnClearSchedule);
        Button btnAddGroup = findViewById(R.id.btnAddGroup);
        Button btnPresetGroups = findViewById(R.id.btnPresetGroups);
        TextView tvFormTitle = findViewById(R.id.tvFormTitle);

        tvFormTitle.setText("创建" + mode);
        tvMode.setText("模式：" + mode);

        // 模式相关区块显隐
        boolean scoreMode = Competition.MODE_SCORE.equals(mode);
        boolean personalMode = "个人赛".equals(mode);
        scoreSection.setVisibility(scoreMode ? View.VISIBLE : View.GONE);
        tvDurationLabel.setVisibility(personalMode ? View.GONE : View.VISIBLE);
        spDuration.setVisibility(personalMode ? View.GONE : View.VISIBLE);
        if (Competition.MODE_TEAM.equals(mode)) {
            tvDurationLabel.setText("赛事进行时间（规定时间，用于超时统计参考）");
        }
        if ("接力赛".equals(mode)) {
            tvDurationLabel.setText("赛事进行时间（规定时间）");
        }

        spDuration.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, DURATION_LABELS));

        // 预约时间
        tvStartTime.setOnClickListener(v -> pickDateTime(true));
        tvEndTime.setOnClickListener(v -> pickDateTime(false));
        btnClearSchedule.setOnClickListener(v -> {
            startTime = 0;
            endTime = 0;
            tvStartTime.setText("");
            tvEndTime.setText("");
            toast("已清除预约时间");
        });

        // 成员
        btnAddMember.setOnClickListener(v -> addMember());
        lvMembers.setOnItemLongClickListener((parent, view, position, id) -> {
            removeMember(position);
            return true;
        });

        // 组别
        btnAddGroup.setOnClickListener(v -> addGroup());
        btnPresetGroups.setOnClickListener(v -> showPresetGroups());
        lvGroups.setOnItemLongClickListener((parent, view, position, id) -> {
            groups.remove(position);
            refreshGroupList();
            return true;
        });
        refreshGroupList();

        // 编辑模式：加载原有数据
        editId = getIntent().getLongExtra("competition_id", -1);
        if (editId != -1) {
            editing = db.getCompetition(editId);
            if (editing != null) {
                tvFormTitle.setText("编辑赛事（" + editing.getMode() + "）");
                mode = editing.getMode();
                tvMode.setText("模式：" + mode);
                etName.setText(editing.getName());
                etDate.setText(editing.getDate());
                etDesc.setText(editing.getDescription());
                startTime = editing.getStartTime();
                endTime = editing.getEndTime();
                if (startTime > 0) {
                    tvStartTime.setText(RaceRecord.formatDateTime(startTime));
                }
                if (endTime > 0) {
                    tvEndTime.setText(RaceRecord.formatDateTime(endTime));
                }
                if (editing.getValidMinutes() > 0) {
                    etValidMinutes.setText(String.valueOf(editing.getValidMinutes()));
                }
                if (editing.getPenaltyPerMin() > 0) {
                    etPenalty.setText(String.valueOf(editing.getPenaltyPerMin()));
                }
                selectDuration(editing.getTimeLimit());
                loadMembers();
                // 编辑模式下隐藏组别区（组别只在创建时批量建）
                etGroup.setVisibility(View.GONE);
                btnAddGroup.setVisibility(View.GONE);
                btnPresetGroups.setVisibility(View.GONE);
                lvGroups.setVisibility(View.GONE);
            }
        }

        btnSave.setOnClickListener(v -> save());
    }

    /** 找到不小于给定分钟数的档位 */
    private void selectDuration(int minutes) {
        int index = 0;
        for (int i = 0; i < DURATION_MINUTES.length; i++) {
            if (DURATION_MINUTES[i] >= minutes) {
                index = i;
                break;
            }
            index = i;
        }
        spDuration.setSelection(index);
    }

    /** 日期 + 时间选择器，选完写入 startTime/endTime */
    private void pickDateTime(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        if (isStart && startTime > 0) {
            cal.setTimeInMillis(startTime);
        } else if (!isStart && endTime > 0) {
            cal.setTimeInMillis(endTime);
        }
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);
        new DatePickerDialog(this, (dp, year, month, day) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, day,
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), 0);
            new TimePickerDialog(this, (tp, hour, minute) -> {
                Calendar result = Calendar.getInstance();
                result.set(year, month, day, hour, minute, 0);
                result.set(Calendar.MILLISECOND, 0);
                long ts = result.getTimeInMillis();
                if (isStart) {
                    startTime = ts;
                    tvStartTime.setText(RaceRecord.formatDateTime(ts));
                } else {
                    endTime = ts;
                    tvEndTime.setText(RaceRecord.formatDateTime(ts));
                }
                if (startTime > 0 && endTime > 0 && endTime <= startTime) {
                    toast("⚠️ 结束时间应晚于开始时间");
                }
            }, picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true).show();
        }, y, m, d).show();
    }

    // ==================== 组别 ====================

    private void addGroup() {
        String name = etGroup.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            toast("请输入组别名");
            return;
        }
        if (groups.contains(name)) {
            toast("该组别已存在");
            return;
        }
        groups.add(name);
        etGroup.setText("");
        refreshGroupList();
    }

    private void showPresetGroups() {
        boolean[] checked = new boolean[PRESET_GROUPS.length];
        new AlertDialog.Builder(this)
                .setTitle("选择常用组别")
                .setMultiChoiceItems(PRESET_GROUPS, checked, (d, which, isChecked) ->
                        checked[which] = isChecked)
                .setPositiveButton("添加", (d, w) -> {
                    for (int i = 0; i < PRESET_GROUPS.length; i++) {
                        if (checked[i] && !groups.contains(PRESET_GROUPS[i])) {
                            groups.add(PRESET_GROUPS[i]);
                        }
                    }
                    refreshGroupList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshGroupList() {
        List<String> display = new ArrayList<>();
        for (String g : groups) {
            display.add("🏷 " + g);
        }
        lvGroups.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    // ==================== 成员管理 ====================

    /** 已发布赛事从云端加载成员，未发布赛事从本地待拉名单加载 */
    private void loadMembers() {
        if (editing != null && editing.getCloudId() != null) {
            new Thread(() -> {
                try {
                    JSONObject resp = ApiClient.get(this,
                            "/api/competitions/" + editing.getCloudId() + "/members");
                    runOnUiThread(() -> {
                        if (resp.optBoolean("ok")) {
                            pendingMembers.clear();
                            JSONArray arr = resp.optJSONArray("data");
                            for (int i = 0; arr != null && i < arr.length(); i++) {
                                try {
                                    pendingMembers.add(
                                            arr.getJSONObject(i).optString("username"));
                                } catch (Exception ignored) {
                                }
                            }
                            refreshMemberList();
                        }
                    });
                } catch (Exception ignored) {
                }
            }).start();
        } else {
            pendingMembers.clear();
            pendingMembers.addAll(loadPendingMembers(this, editId));
            refreshMemberList();
        }
    }

    private void refreshMemberList() {
        List<String> display = new ArrayList<>();
        for (String u : pendingMembers) {
            display.add("👤 " + u);
        }
        lvMembers.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    private void addMember() {
        String username = etMember.getText().toString().trim();
        if (TextUtils.isEmpty(username)) {
            toast("请输入对方学号 / 用户名");
            return;
        }
        if (pendingMembers.contains(username)) {
            toast("该成员已在列表中");
            return;
        }
        // 已发布赛事：实时调云端接口；未发布赛事：先记本地名单
        if (editing != null && editing.getCloudId() != null) {
            new Thread(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("username", username);
                    JSONObject resp = ApiClient.post(this,
                            "/api/competitions/" + editing.getCloudId() + "/members", body);
                    runOnUiThread(() -> {
                        if (resp.optBoolean("ok")) {
                            pendingMembers.add(username);
                            refreshMemberList();
                            etMember.setText("");
                            toast("已添加：" + username
                                    + (resp.optString("name").isEmpty()
                                    ? "" : "（" + resp.optString("name") + "）"));
                        } else {
                            toast(resp.optString("msg", "添加失败"));
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> toast("无法连接服务器：" + e.getMessage()));
                }
            }).start();
        } else {
            pendingMembers.add(username);
            refreshMemberList();
            etMember.setText("");
            toast("已加入名单（发布赛事时一起拉入云端）");
        }
    }

    private void removeMember(int position) {
        String username = pendingMembers.get(position);
        if (editing != null && editing.getCloudId() != null) {
            toast("已发布赛事请在「参赛成员管理」中移除成员");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("移除成员")
                .setMessage("把「" + username + "」移出名单？")
                .setPositiveButton("移除", (d, w) -> {
                    pendingMembers.remove(position);
                    refreshMemberList();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ==================== 保存 ====================

    private void save() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            toast("请填写赛事名称");
            return;
        }
        if (endTime > 0 && startTime > 0 && endTime <= startTime) {
            toast("⚠️ 结束时间必须晚于开始时间");
            return;
        }

        if (editId != -1) {
            saveEdit();
            return;
        }

        // ===== 新建：一场赛事 + 各组别赛事 =====
        List<Competition> created = new ArrayList<>();
        created.add(insertComp(name, null));
        for (String group : groups) {
            created.add(insertComp(name, group));
        }
        // 把待拉成员名单挂到每一场（发布时一起上传）
        for (Competition c : created) {
            savePendingMembers(this, c.getId(), pendingMembers);
        }
        if (groups.isEmpty()) {
            toast("赛事创建成功，去添加检查点吧");
        } else {
            toast("✅ 已创建 1 场主赛事 + " + groups.size() + " 个组别赛事，"
                    + "每个组别发布后有独立加入码");
        }
        finish();
    }

    /** 新建一场赛事；group 非空时名称追加组别后缀 */
    private Competition insertComp(String name, String group) {
        Competition comp = new Competition();
        comp.setName(group == null ? name : name + " · " + group);
        comp.setMode(mode);
        comp.setDate(etDate.getText().toString().trim());
        comp.setDescription(etDesc.getText().toString().trim());
        comp.setStatus("未开始");
        boolean personalMode = "个人赛".equals(mode);
        comp.setTimeLimit(personalMode ? 60
                : DURATION_MINUTES[spDuration.getSelectedItemPosition()]);
        comp.setValidMinutes(Competition.MODE_SCORE.equals(mode)
                ? parseInt(etValidMinutes.getText().toString().trim()) : 0);
        comp.setPenaltyPerMin(Competition.MODE_SCORE.equals(mode)
                ? parseInt(etPenalty.getText().toString().trim()) : 0);
        comp.setStartTime(startTime);
        comp.setEndTime(endTime);
        comp.setId(db.insertCompetition(comp));

        // 组别赛事复制主赛事已有的检查点（编辑过主赛事检查点再建组别时）
        if (editId != -1) {
            for (Checkpoint cp : db.getCheckpoints(editId)) {
                Checkpoint copy = new Checkpoint();
                copy.setCompetitionId(comp.getId());
                copy.setCode(cp.getCode());
                copy.setName(cp.getName());
                copy.setSeq(cp.getSeq());
                copy.setPoints(cp.getPoints());
                copy.setType(cp.getType());
                copy.setRequired(cp.isRequired());
                db.insertCheckpoint(copy);
            }
        }
        return comp;
    }

    /** 编辑已有赛事 */
    private void saveEdit() {
        Competition comp = new Competition();
        comp.setId(editId);
        comp.setName(etName.getText().toString().trim());
        comp.setMode(mode);
        comp.setDate(etDate.getText().toString().trim());
        comp.setDescription(etDesc.getText().toString().trim());
        comp.setStatus(editing.getStatus());
        comp.setTimeLimit("个人赛".equals(mode) ? 60
                : DURATION_MINUTES[spDuration.getSelectedItemPosition()]);
        comp.setValidMinutes(Competition.MODE_SCORE.equals(mode)
                ? parseInt(etValidMinutes.getText().toString().trim()) : 0);
        comp.setPenaltyPerMin(Competition.MODE_SCORE.equals(mode)
                ? parseInt(etPenalty.getText().toString().trim()) : 0);
        comp.setStartTime(startTime);
        comp.setEndTime(endTime);
        comp.setCloudId(editing.getCloudId());
        comp.setJoinKey(editing.getJoinKey());
        db.updateCompetition(comp);
        savePendingMembers(this, editId, pendingMembers);
        if (comp.getCloudId() != null) {
            syncToCloud(comp);
        }
        toast("保存成功");
        finish();
    }

    /** 已发布的赛事：把修改同步到云端 */
    private void syncToCloud(Competition comp) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", comp.getName());
                body.put("mode", comp.getMode());
                body.put("date", comp.getDate() == null ? "" : comp.getDate());
                body.put("description", comp.getDescription() == null ? "" : comp.getDescription());
                body.put("timeLimit", comp.getTimeLimit());
                body.put("validMinutes", comp.getValidMinutes());
                body.put("penaltyPerMin", comp.getPenaltyPerMin());
                body.put("startTime", comp.getStartTime());
                body.put("endTime", comp.getEndTime());
                ApiClient.post(this, "/api/competitions/" + comp.getCloudId(), body);
            } catch (Exception ignored) {
                // 同步失败不打扰用户，下次编辑会再同步
            }
        }).start();
    }

    // ==================== 本地待拉成员名单（SharedPreferences） ====================

    private static final String PREF_MEMBERS = "pending_members";

    public static void savePendingMembers(Context context, long compId, List<String> members) {
        JSONArray arr = new JSONArray();
        for (String m : members) {
            arr.put(m);
        }
        context.getSharedPreferences(PREF_MEMBERS, Context.MODE_PRIVATE)
                .edit().putString("comp_" + compId, arr.toString()).apply();
    }

    public static List<String> loadPendingMembers(Context context, long compId) {
        List<String> list = new ArrayList<>();
        try {
            String json = context.getSharedPreferences(PREF_MEMBERS, Context.MODE_PRIVATE)
                    .getString("comp_" + compId, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void clearPendingMembers(Context context, long compId) {
        context.getSharedPreferences(PREF_MEMBERS, Context.MODE_PRIVATE)
                .edit().remove("comp_" + compId).apply();
    }

    private int parseInt(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

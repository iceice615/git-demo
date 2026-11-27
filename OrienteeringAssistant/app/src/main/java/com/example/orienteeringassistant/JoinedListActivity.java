package com.example.orienteeringassistant;

import android.content.Intent;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 我参加的赛事（被发起者拉进"群"的赛事）
 * 点击自动从云端下载赛道到本地，即可开始比赛
 * 团队赛：下载后选择/创建队伍
 */
public class JoinedListActivity extends AppCompatActivity {

    private DBHelper db;
    private SessionManager session;
    private ListView listView;
    private JSONArray data = new JSONArray();
    private final List<String> display = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joined_list);

        db = DBHelper.getInstance(this);
        session = new SessionManager(this);
        listView = findViewById(R.id.lvJoined);
        listView.setEmptyView(findViewById(R.id.tvEmpty));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                JSONObject item = data.getJSONObject(position);
                confirmJoin(item);
            } catch (Exception e) {
                toast("数据异常");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadJoined();
    }

    private void loadJoined() {
        new Thread(() -> {
            try {
                JSONObject resp = ApiClient.get(this,
                        "/api/competitions/joined?userId=" + session.getUserId());
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        data = resp.optJSONArray("data");
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
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                StringBuilder sb = new StringBuilder();
                sb.append(item.optString("name")).append("\n")
                        .append(item.optString("mode")).append(" ｜ 发起者：")
                        .append(item.optString("ownerName"));
                long start = item.optLong("startTime", 0);
                long end = item.optLong("endTime", 0);
                if (start > 0 || end > 0) {
                    sb.append("\n⏰ ").append(start > 0 ? RaceRecord.formatDateTime(start) : "不限")
                            .append(" ~ ").append(end > 0 ? RaceRecord.formatDateTime(end) : "不限");
                }
                String joinKey = item.optString("joinKey");
                if (!joinKey.isEmpty()) {
                    sb.append("\n🔑 加入码：").append(joinKey);
                }
                String team = item.optString("teamName");
                if (!team.isEmpty()) {
                    sb.append("\n🚩 你的队伍：").append(team);
                }
                display.add(sb.toString());
            }
        } catch (Exception ignored) {
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    /** 确认后把云端赛道下载到本地 */
    private void confirmJoin(JSONObject item) {
        long cloudId = item.optLong("id");
        String name = item.optString("name");
        String mode = item.optString("mode");
        long start = item.optLong("startTime", 0);
        long end = item.optLong("endTime", 0);

        String msg = "下载赛道并进入比赛？\n（下载后不需要网络也能比赛，完赛联网时自动上报成绩）";
        if (start > 0 && System.currentTimeMillis() < start) {
            msg = "⏰ 本赛事 " + RaceRecord.formatDateTime(start) + " 才开始，现在还不能开赛。\n\n可先下载赛道备用。";
        } else if (end > 0 && System.currentTimeMillis() > end) {
            msg = "⏰ 本赛事已于 " + RaceRecord.formatDateTime(end) + " 结束。\n\n仍可查看赛道，但无法开赛。";
        }
        new AlertDialog.Builder(this)
                .setTitle(name + "（" + mode + "）")
                .setMessage(msg)
                .setPositiveButton("进入比赛", (d, w) -> downloadCourse(cloudId))
                .setNegativeButton("取消", null)
                .show();
    }

    private void downloadCourse(long cloudId) {
        toast("正在下载赛道…");
        new Thread(() -> {
            try {
                JSONObject resp = ApiClient.get(this, "/api/competitions/" + cloudId);
                if (!resp.optBoolean("ok")) {
                    runOnUiThread(() -> toast(resp.optString("msg", "下载失败")));
                    return;
                }
                // 云端赛事 → 本地赛事（已有缓存则更新）
                Competition comp = new Competition();
                comp.setCloudId(cloudId);
                comp.setName(resp.optString("name"));
                comp.setMode(resp.optString("mode"));
                comp.setDate(resp.optString("date"));
                comp.setDescription(resp.optString("description"));
                comp.setStatus(resp.optString("status"));
                comp.setTimeLimit(resp.optInt("timeLimit", 60));
                comp.setValidMinutes(resp.optInt("validMinutes", 0));
                comp.setPenaltyPerMin(resp.optInt("penaltyPerMin", 0));
                comp.setStartTime(resp.optLong("startTime", 0));
                comp.setEndTime(resp.optLong("endTime", 0));
                comp.setJoinKey(resp.optString("joinKey"));

                Competition local = db.findCompetitionByCloudId(cloudId);
                long localId;
                if (local == null) {
                    localId = db.insertCompetition(comp);
                } else {
                    comp.setId(local.getId());
                    db.updateCompetition(comp);
                    localId = local.getId();
                }

                // 检查点全量刷新
                db.deleteCheckpointsByCompetition(localId);
                JSONArray cps = resp.optJSONArray("checkpoints");
                for (int i = 0; cps != null && i < cps.length(); i++) {
                    JSONObject c = cps.getJSONObject(i);
                    Checkpoint cp = new Checkpoint();
                    cp.setCompetitionId(localId);
                    cp.setCode(c.optString("code"));
                    cp.setName(c.optString("name"));
                    cp.setSeq(c.optInt("seq", 1));
                    cp.setPoints(c.optInt("points", 10));
                    cp.setType(c.optInt("type", 2));
                    cp.setRequired(c.optBoolean("required", true));
                    cp.setLat(c.has("lat") && !c.isNull("lat") ? c.optDouble("lat") : null);
                    cp.setLng(c.has("lng") && !c.isNull("lng") ? c.optDouble("lng") : null);
                    db.insertCheckpoint(cp);
                }

                long finalLocalId = localId;
                boolean teamMode = comp.isTeamMode();
                runOnUiThread(() -> {
                    if (teamMode) {
                        // 团队赛：先选队伍再进比赛
                        showTeamDialog(cloudId, comp, finalLocalId);
                    } else {
                        toast("赛道已就绪，比赛开始！");
                        enterRace(finalLocalId);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("下载失败：" + e.getMessage()));
            }
        }).start();
    }

    /** 团队赛：加入已有队伍 或 创建新队伍 */
    private void showTeamDialog(long cloudId, Competition comp, long localId) {
        new Thread(() -> {
            // 从成员列表收集已有队伍名
            Set<String> teams = new LinkedHashSet<>();
            try {
                JSONObject resp = ApiClient.get(this,
                        "/api/competitions/" + cloudId + "/members");
                JSONArray arr = resp.optJSONArray("data");
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    String t = arr.getJSONObject(i).optString("teamName");
                    if (!t.isEmpty()) {
                        teams.add(t);
                    }
                }
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                String saved = getSharedPreferences("team_names", MODE_PRIVATE)
                        .getString("comp_" + cloudId, "");
                List<String> options = new ArrayList<>(teams);
                if (!options.isEmpty()) {
                    options.add("＋ 创建新队伍");
                    String[] arr = options.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("选择你的队伍" + (saved.isEmpty() ? "" : "（当前：" + saved + "）"))
                            .setItems(arr, (d, which) -> {
                                String picked = arr[which];
                                if ("＋ 创建新队伍".equals(picked)) {
                                    inputTeamName(cloudId, localId);
                                } else {
                                    joinTeam(cloudId, picked, localId);
                                }
                            })
                            .setNegativeButton("稍后再选", (d, w) -> enterRace(localId))
                            .setCancelable(false)
                            .show();
                } else {
                    inputTeamName(cloudId, localId);
                }
            });
        }).start();
    }

    private void inputTeamName(long cloudId, long localId) {
        EditText input = new EditText(this);
        input.setHint("队伍名（如：红队）");
        new AlertDialog.Builder(this)
                .setTitle("创建 / 加入队伍")
                .setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        toast("队伍名不能为空");
                        inputTeamName(cloudId, localId);
                        return;
                    }
                    joinTeam(cloudId, name, localId);
                })
                .setNegativeButton("稍后再选", (d, w) -> enterRace(localId))
                .setCancelable(false)
                .show();
    }

    private void joinTeam(long cloudId, String teamName, long localId) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("userId", session.getUserId());
                body.put("teamName", teamName);
                JSONObject resp = ApiClient.post(this,
                        "/api/competitions/" + cloudId + "/teams", body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        getSharedPreferences("team_names", MODE_PRIVATE)
                                .edit().putString("comp_" + cloudId, teamName).apply();
                        toast("🚩 已加入队伍：" + teamName);
                        loadJoined();
                        enterRace(localId);
                    } else {
                        toast(resp.optString("msg", "加入队伍失败"));
                        enterRace(localId);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("无法连接服务器：" + e.getMessage());
                    enterRace(localId);
                });
            }
        }).start();
    }

    private void enterRace(long localId) {
        Intent intent = new Intent(this, RaceActivity.class);
        intent.putExtra("competition_id", localId);
        startActivity(intent);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

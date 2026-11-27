package com.example.orienteeringassistant;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

import java.util.List;

/**
 * 赛事列表页
 * - 点击：进入该赛事的检查点管理
 * - 长按：编辑 / 排名 / 发布到云端 / 成员管理 / 删除
 */
public class CompetitionListActivity extends AppCompatActivity {

    private DBHelper db;
    private SessionManager session;
    private ListView lvCompetitions;
    private List<Competition> data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_competition_list);

        db = DBHelper.getInstance(this);
        session = new SessionManager(this);

        Button btnAdd = findViewById(R.id.btnAddCompetition);
        lvCompetitions = findViewById(R.id.lvCompetitions);
        lvCompetitions.setEmptyView(findViewById(R.id.tvEmpty));

        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, CompetitionEditActivity.class)));

        // 点击进入检查点管理
        lvCompetitions.setOnItemClickListener((parent, view, position, id) -> {
            Competition comp = data.get(position);
            Intent intent = new Intent(this, CheckpointListActivity.class);
            intent.putExtra("competition_id", comp.getId());
            startActivity(intent);
        });

        // 长按弹出操作菜单
        lvCompetitions.setOnItemLongClickListener((parent, view, position, id) -> {
            showOptions(data.get(position));
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        data = db.getCompetitions();
        lvCompetitions.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, data));
    }

    private void showOptions(Competition comp) {
        boolean teamMode = comp.isTeamMode();
        String[] items = teamMode
                ? new String[]{"编辑赛事", "查看成绩排名（本机）", "☁ 发布到云端",
                "👥 参赛成员管理", "🚩 团队赛况", "删除赛事"}
                : new String[]{"编辑赛事", "查看成绩排名（本机）", "☁ 发布到云端",
                "👥 参赛成员管理", "删除赛事"};
        new AlertDialog.Builder(this)
                .setTitle(comp.getName())
                .setItems(items, (dialog, which) -> {
                    if (teamMode && which == 4) {
                        openTeamResults(comp);
                        return;
                    }
                    if (teamMode && which == 5) {
                        confirmDelete(comp);
                        return;
                    }
                    if (which == 0) {
                        Intent intent = new Intent(this, CompetitionEditActivity.class);
                        intent.putExtra("competition_id", comp.getId());
                        startActivity(intent);
                    } else if (which == 1) {
                        showRanking(comp);
                    } else if (which == 2) {
                        publishToCloud(comp);
                    } else if (which == 3) {
                        openMemberManage(comp);
                    } else if (which == 4) {
                        confirmDelete(comp);
                    }
                })
                .show();
    }

    /** 团队赛况：各队伍完成状态与排名 */
    private void openTeamResults(Competition comp) {
        if (comp.getCloudId() == null) {
            Toast.makeText(this, "请先「发布到云端」", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, TeamResultsActivity.class);
        intent.putExtra("cloud_id", comp.getCloudId());
        intent.putExtra("comp_name", comp.getName());
        startActivity(intent);
    }

    /** 发布到云端：上传赛事 + 全部检查点 + 待拉成员名单，成员才能看到并参赛 */
    private void publishToCloud(Competition comp) {
        if (comp.getCloudId() != null) {
            Toast.makeText(this, "该赛事已发布过（云端ID " + comp.getCloudId() + "）",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<Checkpoint> checkpoints = db.getCheckpoints(comp.getId());
        if (checkpoints.isEmpty()) {
            Toast.makeText(this, "请先添加检查点再发布", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在发布到云端…", Toast.LENGTH_SHORT).show();
        List<String> members = CompetitionFormActivity.loadPendingMembers(this, comp.getId());
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("ownerId", session.getUserId());
                body.put("name", comp.getName());
                body.put("mode", comp.getMode());
                body.put("date", comp.getDate() == null ? "" : comp.getDate());
                body.put("description", comp.getDescription() == null ? "" : comp.getDescription());
                body.put("timeLimit", comp.getTimeLimit());
                body.put("validMinutes", comp.getValidMinutes());
                body.put("penaltyPerMin", comp.getPenaltyPerMin());
                body.put("startTime", comp.getStartTime());
                body.put("endTime", comp.getEndTime());
                JSONArray memberArray = new JSONArray();
                for (String m : members) {
                    memberArray.put(m);
                }
                body.put("members", memberArray);
                JSONArray cpArray = new JSONArray();
                for (Checkpoint cp : checkpoints) {
                    JSONObject c = new JSONObject();
                    c.put("code", cp.getCode());
                    c.put("name", cp.getName());
                    c.put("seq", cp.getSeq());
                    c.put("points", cp.getPoints());
                    c.put("type", cp.getType());
                    c.put("required", cp.isRequired());
                    cpArray.put(c);
                }
                body.put("checkpoints", cpArray);
                JSONObject resp = ApiClient.post(this, "/api/competitions", body);
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        long cloudId = resp.optLong("id");
                        comp.setCloudId(cloudId);
                        comp.setJoinKey(resp.optString("joinKey"));
                        db.updateCompetition(comp);
                        CompetitionFormActivity.clearPendingMembers(this, comp.getId());
                        Toast.makeText(this, "✅ 发布成功！加入码 " + comp.getJoinKey()
                                        + "，已拉入 " + members.size() + " 名成员",
                                Toast.LENGTH_LONG).show();
                        refreshList();
                        offerPublishSiblings(comp);
                    } else {
                        Toast.makeText(this, resp.optString("msg", "发布失败"),
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "无法连接服务器：" + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    /**
     * 发布成功后：检查同组（同主名称）是否还有未发布的组别赛事，
     * 有则询问是否一起发布，逐场调用 publishToCloud。
     */
    private void offerPublishSiblings(Competition comp) {
        List<Competition> siblings = db.findSiblings(comp);
        List<Competition> unpublished = new java.util.ArrayList<>();
        for (Competition s : siblings) {
            if (s.getCloudId() == null) {
                unpublished.add(s);
            }
        }
        if (unpublished.isEmpty()) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Competition s : unpublished) {
            // 未发布且无检查点的组别会在 publishToCloud 里被拦下提示
            names.append("· ").append(s.getName()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("还有 " + unpublished.size() + " 个组别未发布")
                .setMessage(names + "\n每组发布后有独立加入码，是否现在一起发布？")
                .setPositiveButton("一起发布", (dialog, which) -> {
                    for (Competition s : unpublished) {
                        publishToCloud(s);
                    }
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void openMemberManage(Competition comp) {
        if (comp.getCloudId() == null) {
            Toast.makeText(this, "请先「发布到云端」再管理成员", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, MemberManageActivity.class);
        intent.putExtra("cloud_id", comp.getCloudId());
        intent.putExtra("comp_name", comp.getName());
        startActivity(intent);
    }

    /** 个人赛成绩统计：有效成绩按用时排名 */
    private void showRanking(Competition comp) {
        List<RaceRecord> ranking = db.getRanking(comp.getId());
        StringBuilder sb = new StringBuilder();
        if (ranking.isEmpty()) {
            sb.append("暂无有效成绩");
        } else {
            int rank = 1;
            for (RaceRecord r : ranking) {
                sb.append(rank++).append(". ")
                        .append(r.getUsername()).append("  ")
                        .append(RaceRecord.formatElapsed(r.getElapsed()))
                        .append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(comp.getName() + " · 成绩排名")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    private void confirmDelete(Competition comp) {
        new AlertDialog.Builder(this)
                .setTitle("删除赛事")
                .setMessage("删除「" + comp.getName() + "」将同时删除其检查点和全部成绩记录，确定吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    db.deleteCompetition(comp.getId());
                    refreshList();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}

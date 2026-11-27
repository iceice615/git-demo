package com.example.orienteeringassistant;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.model.Checkpoint;
import com.example.orienteeringassistant.model.Competition;

import java.util.List;

/**
 * 某场赛事的检查点列表
 * - 点击：编辑该检查点
 * - 长按：删除该检查点
 */
public class CheckpointListActivity extends AppCompatActivity {

    private DBHelper db;
    private long competitionId;
    private ListView lvCheckpoints;
    private List<Checkpoint> data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkpoint_list);

        db = DBHelper.getInstance(this);
        competitionId = getIntent().getLongExtra("competition_id", -1);

        TextView tvTitle = findViewById(R.id.tvTitle);
        Competition comp = db.getCompetition(competitionId);
        tvTitle.setText((comp == null ? "赛事" : comp.getName()) + " · 检查点管理");

        Button btnAdd = findViewById(R.id.btnAddCheckpoint);
        lvCheckpoints = findViewById(R.id.lvCheckpoints);
        lvCheckpoints.setEmptyView(findViewById(R.id.tvEmpty));

        btnAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, CheckpointEditActivity.class);
            intent.putExtra("competition_id", competitionId);
            startActivity(intent);
        });

        Button btnSync = findViewById(R.id.btnSyncSiblings);
        btnSync.setOnClickListener(v -> syncToSiblings());

        lvCheckpoints.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, CheckpointEditActivity.class);
            intent.putExtra("competition_id", competitionId);
            intent.putExtra("checkpoint_id", data.get(position).getId());
            startActivity(intent);
        });

        lvCheckpoints.setOnItemLongClickListener((parent, view, position, id) -> {
            Checkpoint cp = data.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("删除检查点")
                    .setMessage("确定删除「" + cp.getName() + "（" + cp.getCode() + "）」吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        db.deleteCheckpoint(cp.getId());
                        refreshList();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshList();
    }

    private void refreshList() {
        data = db.getCheckpoints(competitionId);
        lvCheckpoints.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, data));
    }

    /**
     * 把本场赛事的检查点整套复制到同组（同主名称）的其它未发布赛事，
     * 组别名不同但赛道相同，建一次全组通用。
     */
    private void syncToSiblings() {
        Competition comp = db.getCompetition(competitionId);
        if (comp == null) {
            return;
        }
        List<Checkpoint> mine = db.getCheckpoints(competitionId);
        if (mine.isEmpty()) {
            toast("本场还没有检查点，先添加再同步");
            return;
        }
        List<Competition> siblings = db.findSiblings(comp);
        List<Competition> targets = new java.util.ArrayList<>();
        int skipped = 0;
        for (Competition s : siblings) {
            if (s.getCloudId() == null) {
                targets.add(s);
            } else {
                skipped++;
            }
        }
        if (targets.isEmpty()) {
            toast(skipped > 0
                    ? "同组赛事都已发布到云端，请在云端编辑"
                    : "没有同组赛事（在创建赛事时添加组别才会有）");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (Competition t : targets) {
            names.append("· ").append(t.getName()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("同步检查点到 " + targets.size() + " 个同组赛事")
                .setMessage("以下赛事的检查点将被【整体替换】为本场的 "
                        + mine.size() + " 个检查点：\n\n" + names
                        + (skipped > 0 ? "\n（已发布的 " + skipped + " 场自动跳过）" : ""))
                .setPositiveButton("同步", (dialog, which) -> {
                    for (Competition t : targets) {
                        db.deleteCheckpointsByCompetition(t.getId());
                        for (Checkpoint cp : mine) {
                            Checkpoint copy = new Checkpoint();
                            copy.setCompetitionId(t.getId());
                            copy.setCode(cp.getCode());
                            copy.setName(cp.getName());
                            copy.setSeq(cp.getSeq());
                            copy.setPoints(cp.getPoints());
                            copy.setType(cp.getType());
                            copy.setRequired(cp.isRequired());
                            copy.setLat(cp.getLat());
                            copy.setLng(cp.getLng());
                            db.insertCheckpoint(copy);
                        }
                    }
                    toast("✅ 已同步到 " + targets.size() + " 场赛事");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }
}

package com.example.orienteeringassistant;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.util.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 团队赛况（发起者与成员都能看）
 * - 每队：成员完成状态、自由点覆盖、队伍成绩（最后完赛队员的时间）
 * - 有效队伍排前，按队伍用时升序
 */
public class TeamResultsActivity extends AppCompatActivity {

    private long cloudId;
    private JSONArray data = new JSONArray();
    private final List<String> display = new ArrayList<>();
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_results);

        cloudId = getIntent().getLongExtra("cloud_id", -1);
        String compName = getIntent().getStringExtra("comp_name");

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText((compName == null ? "赛事" : compName) + " · 团队赛况");

        listView = findViewById(R.id.lvTeams);
        listView.setEmptyView(findViewById(R.id.tvEmpty));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTeams();
    }

    private void loadTeams() {
        new Thread(() -> {
            try {
                JSONObject resp = ApiClient.get(this,
                        "/api/competitions/" + cloudId + "/teams");
                runOnUiThread(() -> {
                    if (resp.optBoolean("ok")) {
                        data = resp.optJSONArray("data");
                        int freeTotal = resp.optInt("freeTotal", 0);
                        TextView tvSummary = findViewById(R.id.tvSummary);
                        tvSummary.setText("规则：必达点每人按顺序打，自由点（共 " + freeTotal
                                + " 个）全队任一人打即算、不可漏点；\n队伍成绩 = 最后完赛队员的时间。");
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
            int rank = 0;
            int validCount = 0;
            for (int i = 0; i < data.length(); i++) {
                JSONObject t = data.getJSONObject(i);
                boolean valid = t.optBoolean("teamValid");
                if (valid) {
                    rank++;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(valid ? "🥇 第" + rank + "名  " : "")
                        .append("🚩 ").append(t.optString("teamName"))
                        .append("（").append(t.optInt("memberCount")).append("人）  ")
                        .append(valid ? "✅ 有效" : "❌ 未完成")
                        .append("\n");
                if (t.optLong("teamFinishTime") > 0) {
                    sb.append("队伍用时：以最后完赛队员计  ")
                            .append(RaceRecord.formatElapsed(t.optLong("teamFinishTime")
                                    - firstStart(t)))
                            .append("\n");
                }
                sb.append("自由点：").append(t.optInt("freeCovered")).append("/")
                        .append(t.optInt("freeTotal"));
                JSONArray missing = t.optJSONArray("freeMissing");
                if (missing != null && missing.length() > 0) {
                    sb.append("  缺：");
                    for (int j = 0; j < missing.length(); j++) {
                        sb.append(missing.optString(j)).append(" ");
                    }
                }
                sb.append("\n成员：");
                JSONArray members = t.optJSONArray("members");
                for (int j = 0; members != null && j < members.length(); j++) {
                    JSONObject m = members.getJSONObject(j);
                    String showName = m.optString("name").isEmpty()
                            ? m.optString("username") : m.optString("name");
                    sb.append(showName)
                            .append(m.optBoolean("valid") ? "✅" : (m.optBoolean("finished") ? "❌" : "⏳"))
                            .append("  ");
                }
                display.add(sb.toString());
                if (valid) validCount++;
            }
        } catch (Exception ignored) {
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    /** 用最后完赛队员的时间粗略展示队伍用时（起点以该队首个上传成绩为准） */
    private long firstStart(JSONObject team) {
        try {
            JSONArray members = team.optJSONArray("members");
            // 服务器返回没有直接给每人的 startTime，这里退化为用 teamFinishTime 与成员 elapsed 最大值估算
            long maxElapsed = 0;
            for (int i = 0; members != null && i < members.length(); i++) {
                maxElapsed = Math.max(maxElapsed, members.getJSONObject(i).optLong("elapsed"));
            }
            return team.optLong("teamFinishTime") - maxElapsed;
        } catch (Exception e) {
            return team.optLong("teamFinishTime");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

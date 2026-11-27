package com.example.orienteeringassistant;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.db.SessionManager;
import com.example.orienteeringassistant.model.RaceRecord;

import java.util.List;

/**
 * 我的成绩页：显示当前用户的全部比赛记录
 */
public class RecordsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        DBHelper db = DBHelper.getInstance(this);
        SessionManager session = new SessionManager(this);

        ListView lvRecords = findViewById(R.id.lvRecords);
        lvRecords.setEmptyView(findViewById(R.id.tvEmpty));

        List<RaceRecord> records = db.getRecordsByUser(session.getUserId());
        lvRecords.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, records));

        // 点击查看成绩详情（打卡时间轴 + 分段用时 + 排名）
        lvRecords.setOnItemClickListener((parent, view, position, id) -> {
            RaceRecord record = records.get(position);
            if (!record.isFinished()) {
                // 未完成的比赛，直接回到比赛界面继续
                Intent intent = new Intent(this, RaceActivity.class);
                intent.putExtra("competition_id", record.getCompetitionId());
                startActivity(intent);
                return;
            }
            Intent intent = new Intent(this, RaceResultActivity.class);
            intent.putExtra("race_id", record.getId());
            startActivity(intent);
        });
    }
}

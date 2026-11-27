package com.example.orienteeringassistant;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.model.Competition;

import java.util.List;

/**
 * 开始比赛第一步：选择要参加的赛事
 */
public class RaceSelectActivity extends AppCompatActivity {

    private DBHelper db;
    private List<Competition> data;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_race_select);

        db = DBHelper.getInstance(this);
        ListView lvCompetitions = findViewById(R.id.lvCompetitions);
        lvCompetitions.setEmptyView(findViewById(R.id.tvEmpty));

        lvCompetitions.setOnItemClickListener((parent, view, position, id) -> {
            Competition comp = data.get(position);
            // 没有检查点的赛事不能开始比赛
            if (db.getCheckpoints(comp.getId()).isEmpty()) {
                Toast.makeText(this, "该赛事还没有检查点，请先在赛事管理中添加", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(this, RaceActivity.class);
            intent.putExtra("competition_id", comp.getId());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        data = db.getCompetitions();
        ListView lvCompetitions = findViewById(R.id.lvCompetitions);
        lvCompetitions.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, data));
    }
}

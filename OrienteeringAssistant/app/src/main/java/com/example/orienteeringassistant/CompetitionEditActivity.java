package com.example.orienteeringassistant;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.model.Competition;

/**
 * 赛事创建入口：选择比赛模式（四个模式有各自独立的创建界面）
 * 带 competition_id 进来 = 编辑模式 → 直接跳到对应模式的表单页
 */
public class CompetitionEditActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_competition_edit);

        // 编辑模式：直接跳到该赛事所属模式的表单
        long editId = getIntent().getLongExtra("competition_id", -1);
        if (editId != -1) {
            Competition comp = DBHelper.getInstance(this).getCompetition(editId);
            openForm(comp != null ? comp.getMode() : Competition.MODE_SCORE, editId);
            return;
        }

        Button btnPersonal = findViewById(R.id.btnModePersonal);
        Button btnScore = findViewById(R.id.btnModeScore);
        Button btnTeam = findViewById(R.id.btnModeTeam);
        Button btnRelay = findViewById(R.id.btnModeRelay);

        btnPersonal.setOnClickListener(v -> openForm("个人赛", -1));
        btnScore.setOnClickListener(v -> openForm(Competition.MODE_SCORE, -1));
        btnTeam.setOnClickListener(v -> openForm(Competition.MODE_TEAM, -1));
        btnRelay.setOnClickListener(v -> openForm("接力赛", -1));
    }

    private void openForm(String mode, long editId) {
        Intent intent = new Intent(this, CompetitionFormActivity.class);
        intent.putExtra(CompetitionFormActivity.EXTRA_MODE, mode);
        if (editId != -1) {
            intent.putExtra("competition_id", editId);
        }
        startActivity(intent);
        finish();
    }
}

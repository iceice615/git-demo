package com.example.orienteeringassistant;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.orienteeringassistant.db.DBHelper;
import com.example.orienteeringassistant.model.Checkpoint;
import com.example.orienteeringassistant.util.QrUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 新增 / 编辑检查点页
 * - 打卡顺序：下拉 1-100 直接选
 * - 团队赛：必达点 / 自由点 属性
 * - 可直接生成该点位的二维码图片，方便打印张贴
 */
public class CheckpointEditActivity extends AppCompatActivity {

    private DBHelper db;
    private long competitionId;
    private long editId = -1;

    private EditText etCode;
    private EditText etName;
    private Spinner spSeq;
    private EditText etPoints;
    private Spinner spType;
    private Spinner spRequired;

    /** 下拉框顺序与 Checkpoint 类型常量的映射 */
    private static final String[] TYPE_LABELS = {"起点", "检查点", "终点"};
    private static final String[] REQUIRED_LABELS = {"必达点（每人按顺序打）", "自由点（全队任意一人打即算）"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkpoint_edit);

        db = DBHelper.getInstance(this);
        competitionId = getIntent().getLongExtra("competition_id", -1);

        etCode = findViewById(R.id.etCode);
        etName = findViewById(R.id.etName);
        spSeq = findViewById(R.id.spSeq);
        etPoints = findViewById(R.id.etPoints);
        spType = findViewById(R.id.spType);
        spRequired = findViewById(R.id.spRequired);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnQr = findViewById(R.id.btnQr);

        // 点位类型下拉框：起点 / 检查点 / 终点
        spType.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, TYPE_LABELS));
        spType.setSelection(1); // 默认普通检查点

        // 团队赛属性：必达 / 自由
        spRequired.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, REQUIRED_LABELS));

        // 打卡顺序：1-100 下拉
        List<String> seqs = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            seqs.add(String.valueOf(i));
        }
        spSeq.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, seqs));
        spSeq.setSelection(0);

        // 编辑模式：加载原有数据
        editId = getIntent().getLongExtra("checkpoint_id", -1);
        if (editId != -1) {
            Checkpoint cp = db.getCheckpoint(editId);
            if (cp != null) {
                etCode.setText(cp.getCode());
                etName.setText(cp.getName());
                spSeq.setSelection(Math.max(0, Math.min(99, cp.getSeq() - 1)));
                etPoints.setText(String.valueOf(cp.getPoints()));
                spType.setSelection(Math.max(0, Math.min(2, cp.getType() - 1)));
                spRequired.setSelection(cp.isRequired() ? 0 : 1);
            }
        }

        btnSave.setOnClickListener(v -> save());
        btnQr.setOnClickListener(v -> showQr());
    }

    private void save() {
        String code = etCode.getText().toString().trim().toUpperCase();
        String name = etName.getText().toString().trim();

        if (TextUtils.isEmpty(code)) {
            toast("请填写检查点代码（二维码内容）");
            return;
        }
        if (TextUtils.isEmpty(name)) {
            toast("请填写检查点名称");
            return;
        }

        Checkpoint cp = new Checkpoint();
        cp.setCompetitionId(competitionId);
        cp.setCode(code);
        cp.setName(name);
        cp.setSeq(spSeq.getSelectedItemPosition() + 1); // 1-100 下拉
        cp.setPoints(parseInt(etPoints.getText().toString().trim(), 10));
        cp.setType(spType.getSelectedItemPosition() + 1); // 1=起点 2=检查点 3=终点
        cp.setRequired(spRequired.getSelectedItemPosition() == 0); // 0=必达 1=自由
        // 保留历史坐标（老数据），不再让用户填写
        Checkpoint old = editId != -1 ? db.getCheckpoint(editId) : null;
        if (old != null) {
            cp.setLat(old.getLat());
            cp.setLng(old.getLng());
        }

        if (editId == -1) {
            long id = db.insertCheckpoint(cp);
            if (id == -1) {
                toast("保存失败：代码「" + code + "」在本赛事中已存在");
                return;
            }
            toast("检查点添加成功");
        } else {
            cp.setId(editId);
            db.updateCheckpoint(cp);
            toast("保存成功");
        }
        finish();
    }

    /** 弹窗展示该点位的二维码（内容格式：ORI|CP|代码） */
    private void showQr() {
        String code = etCode.getText().toString().trim();
        if (TextUtils.isEmpty(code)) {
            toast("请先填写检查点代码");
            return;
        }
        String content = QrUtils.contentFor(code);
        Bitmap bitmap = QrUtils.makeQrBitmap(content, 700);
        if (bitmap == null) {
            toast("二维码生成失败");
            return;
        }
        View view = getLayoutInflater().inflate(R.layout.dialog_qr, null);
        ImageView ivQr = view.findViewById(R.id.ivQr);
        TextView tvQrContent = view.findViewById(R.id.tvQrContent);
        ivQr.setImageBitmap(bitmap);
        tvQrContent.setText(content);
        new AlertDialog.Builder(this)
                .setTitle("检查点二维码")
                .setView(view)
                .setPositiveButton("关闭", null)
                .show();
    }

    private int parseInt(String text, int defaultValue) {
        try {
            return Integer.parseInt(text);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

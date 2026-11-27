package com.example.orienteeringassistant;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.orienteeringassistant.model.RaceRecord;
import com.example.orienteeringassistant.util.ApiClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 全员成绩（发起者与成员都能看）
 * - 点击任意一条 → 查看该成员的打卡时间轴 + GPS 轨迹图
 * - 发起者可导出 CSV 表格 / PDF 成绩单
 */
public class MemberResultsActivity extends AppCompatActivity {

    private long cloudId;
    private String compName;
    private JSONArray data = new JSONArray();
    private final List<String> display = new ArrayList<>();
    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_results);

        cloudId = getIntent().getLongExtra("cloud_id", -1);
        compName = getIntent().getStringExtra("comp_name");

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText((compName == null ? "赛事" : compName) + " · 全员成绩");

        listView = findViewById(R.id.lvResults);
        listView.setEmptyView(findViewById(R.id.tvEmpty));

        Button btnCsv = findViewById(R.id.btnExportCsv);
        Button btnPdf = findViewById(R.id.btnExportPdf);
        btnCsv.setOnClickListener(v -> exportCsv());
        btnPdf.setOnClickListener(v -> exportPdf());

        listView.setOnItemClickListener((parent, view, position, id) -> {
            try {
                openDetail(data.getJSONObject(position));
            } catch (Exception e) {
                toast("数据异常");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadResults();
    }

    private void loadResults() {
        new Thread(() -> {
            try {
                JSONObject resp = ApiClient.get(this,
                        "/api/competitions/" + cloudId + "/results");
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
                JSONObject r = data.getJSONObject(i);
                boolean valid = r.optBoolean("valid");
                boolean scoreMode = "积分赛".equals(r.optString("mode"));
                String result = scoreMode
                        ? r.optInt("score") + " 分"
                        : "用时 " + RaceRecord.formatElapsed(r.optLong("elapsed"));
                String name = r.optString("name").isEmpty()
                        ? r.optString("username") : r.optString("name");
                String team = r.optString("teamName");
                display.add(name + "（" + r.optString("username") + "）"
                        + (team.isEmpty() ? "" : " 🚩" + team)
                        + "\n" + result + (valid ? "  ✅ 有效" : "  ❌ 无效"));
            }
        } catch (Exception ignored) {
        }
        listView.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    /** 打开云端成绩详情（时间轴 + 轨迹图） */
    private void openDetail(JSONObject r) {
        Intent intent = new Intent(this, RaceResultActivity.class);
        intent.putExtra("cloud_race_id", r.optLong("id"));
        intent.putExtra("cloud_comp_name", compName);
        intent.putExtra("cloud_username",
                r.optString("name").isEmpty() ? r.optString("username") : r.optString("name"));
        intent.putExtra("cloud_mode", r.optString("mode"));
        intent.putExtra("cloud_start_time", r.optLong("startTime"));
        intent.putExtra("cloud_elapsed", r.optLong("elapsed"));
        intent.putExtra("cloud_score", r.optInt("score"));
        intent.putExtra("cloud_valid", r.optBoolean("valid"));
        intent.putExtra("cloud_message", r.optString("message"));
        startActivity(intent);
    }

    // ==================== 导出 ====================

    private File exportsDir() {
        File dir = new File(getExternalFilesDir(null), "exports");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** 导出 CSV 成绩表格并用系统分享 */
    private void exportCsv() {
        if (data.length() == 0) {
            toast("暂无成绩可导出");
            return;
        }
        try {
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                    .format(new Date());
            File file = new File(exportsDir(), "成绩_" + stamp + ".csv");
            StringBuilder sb = new StringBuilder();
            sb.append("\uFEFF"); // UTF-8 BOM，让 Excel 正确识别中文
            sb.append("姓名,学号,班级,队伍,模式,用时,积分,是否有效,备注\n");
            for (int i = 0; i < data.length(); i++) {
                JSONObject r = data.getJSONObject(i);
                boolean scoreMode = "积分赛".equals(r.optString("mode"));
                sb.append(r.optString("name")).append(',')
                        .append(r.optString("username")).append(',')
                        .append(r.optString("clazz")).append(',')
                        .append(r.optString("teamName")).append(',')
                        .append(r.optString("mode")).append(',')
                        .append(scoreMode ? "" : RaceRecord.formatElapsed(r.optLong("elapsed"))).append(',')
                        .append(scoreMode ? String.valueOf(r.optInt("score")) : "").append(',')
                        .append(r.optBoolean("valid") ? "有效" : "无效").append(',')
                        .append(r.optString("message")).append('\n');
            }
            try (OutputStream os = new FileOutputStream(file)) {
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
            shareFile(file, "text/csv");
            toast("已导出：" + file.getName());
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    /** 导出 PDF 成绩单（PdfDocument 绘制简单表格） */
    private void exportPdf() {
        if (data.length() == 0) {
            toast("暂无成绩可导出");
            return;
        }
        try {
            PdfDocument doc = new PdfDocument();
            Paint title = new Paint();
            title.setTextSize(18);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setColor(Color.BLACK);
            Paint head = new Paint();
            head.setTextSize(11);
            head.setTypeface(Typeface.DEFAULT_BOLD);
            head.setColor(Color.WHITE);
            Paint cell = new Paint();
            cell.setTextSize(11);
            cell.setColor(Color.BLACK);

            int pageWidth = 842;  // A4 横向
            int pageHeight = 595;
            int rowH = 28;
            int rowsPerPage = (pageHeight - 120) / rowH;
            int totalPages = Math.max(1,
                    (int) Math.ceil((data.length() + 1.0) / rowsPerPage));

            int rowIndex = -1; // -1 表示表头
            for (int p = 0; p < totalPages; p++) {
                PdfDocument.PageInfo info =
                        new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, p + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas canvas = page.getCanvas();
                if (p == 0) {
                    canvas.drawText((compName == null ? "赛事" : compName) + " · 成绩单",
                            40, 50, title);
                }
                int y = 100;
                int startRow = p == 0 ? -1 : p * rowsPerPage;
                for (int r = startRow; r < data.length() + 1 && y < pageHeight - 20; r++) {
                    String[] cols;
                    if (r < 0) {
                        cols = new String[]{"姓名", "学号", "队伍", "用时", "积分", "有效", "备注"};
                    } else {
                        JSONObject item = data.getJSONObject(r);
                        boolean scoreMode = "积分赛".equals(item.optString("mode"));
                        cols = new String[]{
                                item.optString("name").isEmpty()
                                        ? item.optString("username") : item.optString("name"),
                                item.optString("username"),
                                item.optString("teamName"),
                                scoreMode ? "-" : RaceRecord.formatElapsed(item.optLong("elapsed")),
                                scoreMode ? String.valueOf(item.optInt("score")) : "-",
                                item.optBoolean("valid") ? "是" : "否",
                                item.optString("message")};
                    }
                    if (r < 0) {
                        Paint bg = new Paint();
                        bg.setColor(Color.parseColor("#2E6DF6"));
                        canvas.drawRect(30, y - 18, pageWidth - 30, y + 10, bg);
                    }
                    Paint p2 = r < 0 ? head : cell;
                    int x = 40;
                    int[] widths = {110, 110, 100, 90, 60, 50, 260};
                    for (int c = 0; c < cols.length; c++) {
                        canvas.drawText(clip(cols[c], widths[c] / 6), x, y, p2);
                        x += widths[c];
                    }
                    y += rowH;
                }
                doc.finishPage(page);
            }

            String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                    .format(new Date());
            File file = new File(exportsDir(), "成绩单_" + stamp + ".pdf");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                doc.writeTo(fos);
            }
            doc.close();
            shareFile(file, "application/pdf");
            toast("已导出：" + file.getName());
        } catch (Exception e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    private String clip(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        return s.length() > maxChars ? s.substring(0, maxChars - 1) + "…" : s;
    }

    /** 调系统分享面板发送文件 */
    private void shareFile(File file, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType(mime);
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享成绩文件"));
        } catch (Exception e) {
            toast("文件已保存到：" + file.getAbsolutePath());
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

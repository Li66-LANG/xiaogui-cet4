package com.cet4.cards;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 推送统计：概览、近 7 天、掌握进度、活跃时段、总记不住的词、最近记录 */
public class StatsActivity extends AppCompatActivity {

    private Tts tts;
    private WordDb db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tts = new Tts(this);
        db = WordDb.get(this);

        findViewById(R.id.btnViewAll).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, HistoryActivity.class)));

        render();

        fillMate();
    }

    private void render() {
        setText(R.id.tvToday, String.valueOf(db.todayCount()));
        setText(R.id.tvTotal, String.valueOf(db.totalSent()));
        setText(R.id.tvStreak, String.valueOf(db.streakDays()));
        setText(R.id.tvCover, String.valueOf(db.pushedWordCount()));

        buildDayChart(db.lastDays(7));
        buildMastery();
        buildHourChart(db.hourHistogram());
        buildStubborn();
        buildRecent();
    }

    // ---------------- 近 7 天 ----------------

    private void buildDayChart(int[] data) {
        LinearLayout host = findViewById(R.id.chartWeek);
        host.removeAllViews();
        int max = 1;
        for (int v : data) if (v > max) max = v;

        Calendar cal = Calendar.getInstance();
        String[] labels = new String[data.length];
        for (int i = data.length - 1; i >= 0; i--) {
            labels[i] = i == data.length - 1
                    ? getString(R.string.today_label)
                    : (cal.get(Calendar.MONTH) + 1) + "/" + cal.get(Calendar.DAY_OF_MONTH);
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        int maxBarH = dp(74);
        for (int i = 0; i < data.length; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            host.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            TextView val = new TextView(this);
            val.setText(data[i] > 0 ? String.valueOf(data[i]) : "");
            val.setTextSize(10);
            val.setTextColor(color(R.color.text_sub));
            val.setGravity(Gravity.CENTER);
            col.addView(val, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(15)));

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.addView(box, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, maxBarH));

            View bar = new View(this);
            int h = data[i] <= 0 ? dp(3) : Math.max(dp(8), maxBarH * data[i] / max);
            bar.setBackground(roundRect(data[i] <= 0 ? R.color.divider : R.color.primary, 5));
            box.addView(bar, new LinearLayout.LayoutParams(dp(18), h));

            TextView lbl = new TextView(this);
            lbl.setText(labels[i]);
            lbl.setTextSize(9);
            lbl.setTextColor(color(R.color.text_sub));
            lbl.setGravity(Gravity.CENTER);
            lbl.setSingleLine(true);
            col.addView(lbl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
        }
    }

    // ---------------- 掌握进度 ----------------

    private void buildMastery() {
        int hard = db.countStatus(1);
        int fuzzy = db.countStatus(2);
        int known = db.countStatus(3);
        int fresh = db.countStatus(0);

        LinearLayout bar = findViewById(R.id.barMastery);
        bar.removeAllViews();
        bar.setClipToOutline(true);
        GradientDrawable track = new GradientDrawable();
        track.setColor(color(R.color.divider));
        track.setCornerRadius(dp(7));
        bar.setBackground(track);

        int[] counts = {hard, fuzzy, known, fresh};
        int[] colors = {R.color.st_hard, R.color.st_fuzzy, R.color.st_known, R.color.st_new};
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] <= 0) continue;
            View seg = new View(this);
            seg.setBackgroundColor(color(colors[i]));
            bar.addView(seg, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, counts[i]));
        }

        setText(R.id.tvMastery, getString(R.string.mastery_fmt, hard, fuzzy, known, fresh));
    }

    // ---------------- 活跃时段 ----------------

    private void buildHourChart(int[] data) {
        LinearLayout host = findViewById(R.id.chartHour);
        host.removeAllViews();
        int max = 1;
        int peak = -1;
        int total = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] > max) max = data[i];
            total += data[i];
            if (data[i] > 0 && (peak < 0 || data[i] > data[peak])) peak = i;
        }

        int maxBarH = dp(58);
        for (int i = 0; i < data.length; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            host.addView(col, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            col.addView(box, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, maxBarH));

            View bar = new View(this);
            int h = data[i] <= 0 ? dp(2) : Math.max(dp(6), maxBarH * data[i] / max);
            bar.setBackground(roundRect(data[i] <= 0 ? R.color.divider
                    : i == peak ? R.color.st_fuzzy : R.color.primary, 3));
            box.addView(bar, new LinearLayout.LayoutParams(dp(7), h));

            TextView lbl = new TextView(this);
            lbl.setText((i == 0 || i == 6 || i == 12 || i == 18) ? String.valueOf(i) : "");
            lbl.setTextSize(9);
            lbl.setTextColor(color(R.color.text_sub));
            lbl.setGravity(Gravity.CENTER);
            col.addView(lbl, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(14)));
        }

        if (total <= 0 || peak < 0) {
            setText(R.id.tvPeak, getString(R.string.hour_none));
        } else {
            setText(R.id.tvPeak, getString(R.string.hour_peak_fmt, peak, (peak + 1) % 24, data[peak]));
        }
    }

    // ---------------- 总记不住的词 ----------------

    private void buildStubborn() {
        LinearLayout host = findViewById(R.id.listStubborn);
        host.removeAllViews();
        List<LogEntry> list = db.stubborn(2, 8);
        if (list.isEmpty()) {
            host.addView(hint(getString(R.string.stubborn_empty)));
            return;
        }
        for (final LogEntry e : list) {
            LinearLayout row = row();
            row.addView(word(e.word, 17));

            TextView times = new TextView(this);
            times.setText(getString(R.string.times_fmt, e.times));
            times.setTextSize(11);
            times.setTextColor(color(R.color.st_hard));
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tlp.leftMargin = dp(6);
            row.addView(times, tlp);

            row.addView(meaning(e.meaning));

            row.setOnClickListener(v -> tts.speak(e.word));
            row.setOnLongClickListener(v -> {
                Word w = db.byWord(e.word);
                if (w != null) {
                    db.setStatus(w.id, 3);
                    Toast.makeText(this, R.string.marked_known, Toast.LENGTH_SHORT).show();
                    render();
                }
                return true;
            });
            host.addView(row);
        }
    }

    // ---------------- 最近推送 ----------------

    private void buildRecent() {
        LinearLayout host = findViewById(R.id.listRecent);
        host.removeAllViews();
        List<LogEntry> list = db.recentLogs(30);
        if (list.isEmpty()) {
            host.addView(hint(getString(R.string.recent_empty)));
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("M/d HH:mm", Locale.CHINA);
        for (final LogEntry e : list) {
            LinearLayout row = row();

            TextView time = new TextView(this);
            time.setText(fmt.format(new Date(e.ts)));
            time.setTextSize(10);
            time.setTextColor(color(R.color.text_sub));
            time.setSingleLine(true);
            row.addView(time, new LinearLayout.LayoutParams(dp(58),
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            row.addView(word(e.word, 15));
            row.addView(meaning(e.meaning));

            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(color(e.rated == 1 ? R.color.st_known
                    : e.rated == 0 ? R.color.st_hard : R.color.divider));
            dot.setBackground(g);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(7), dp(7));
            dlp.leftMargin = dp(8);
            row.addView(dot, dlp);

            row.setOnClickListener(v -> tts.speak(e.word));
            host.addView(row);
        }
    }

    // ---------------- 小工具 ----------------

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(9), dp(16), dp(9));
        return row;
    }

    private TextView word(String s, int sp) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color(R.color.text));
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setSingleLine(true);
        return v;
    }

    private TextView meaning(String s) {
        TextView v = new TextView(this);
        v.setText(s == null ? "" : s);
        v.setTextSize(12);
        v.setTextColor(color(R.color.text_sub));
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView hint(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(13);
        v.setTextColor(color(R.color.text_sub));
        v.setPadding(dp(16), dp(12), dp(16), dp(12));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    private GradientDrawable roundRect(int colorRes, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(colorRes));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void setText(int id, String s) {
        TextView v = findViewById(id);
        if (v != null) v.setText(s);
    }

    private int color(int res) {
        return androidx.core.content.ContextCompat.getColor(this, res);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    /** 顶部陪伴卡：龟龟已经陪你第 N 天 */
    private void fillMate() {
        try {
            int streak = db.streakDays();
            int today = db.todayCount();
            int known = db.countStatus(3);
            ((android.widget.TextView) findViewById(R.id.tvMateTitle))
                    .setText(getString(R.string.mate_day_fmt, Math.max(1, streak)));
            ((android.widget.TextView) findViewById(R.id.tvMateSub))
                    .setText(getString(R.string.mate_stats_sub, today, known));
            int left = Math.max(1, 7 - streak);
            ((android.widget.TextView) findViewById(R.id.tvMateBadge))
                    .setText(getString(R.string.mate_streak_fmt, streak, left));
        } catch (Throwable ignored) {
        }
    }
}

package com.cet4.cards;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 每天的推送明细：按天分组，看得到某天到底弹了哪些词 */
public class HistoryActivity extends AppCompatActivity {

    private static final int DAYS = 30;

    private Tts tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        tts = new Tts(this);

        List<LogEntry> all = WordDb.get(this).logsSince(DAYS);

        try {
            ((TextView) findViewById(R.id.tvMateTitle))
                    .setText(getString(R.string.mate_history_fmt, WordDb.get(this).todayCount()));
            ((TextView) findViewById(R.id.tvMateSub))
                    .setText(R.string.mate_history_hint);
        } catch (Throwable ignored) {
        }
        TextView summary = findViewById(R.id.tvSummary);
        RecyclerView rv = findViewById(R.id.rvLogs);

        if (all.isEmpty()) {
            summary.setText(R.string.history_empty);
            rv.setVisibility(View.GONE);
            return;
        }

        summary.setText(getString(R.string.history_days_fmt, DAYS, all.size()));

        rv.setLayoutManager(new LinearLayoutManager(this));
        LogAdapter adapter = new LogAdapter(word -> tts.speak(word));
        rv.setAdapter(adapter);
        adapter.submit(group(all));
    }

    /** 把倒序的记录切成「日期标题 + 当天明细」的扁平列表 */
    private List<LogAdapter.Row> group(List<LogEntry> all) {
        List<LogAdapter.Row> rows = new ArrayList<>();
        int i = 0;
        while (i < all.size()) {
            LogEntry first = all.get(i);
            int j = i;
            while (j < all.size() && all.get(j).day == first.day) j++;

            LogAdapter.Row head = new LogAdapter.Row();
            head.header = true;
            head.title = getString(R.string.history_day_fmt, dayLabel(first.ts), j - i);
            rows.add(head);

            for (int k = i; k < j; k++) {
                LogAdapter.Row r = new LogAdapter.Row();
                r.item = all.get(k);
                rows.add(r);
            }
            i = j;
        }
        return rows;
    }

    private String dayLabel(long ts) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts);
        int key = c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
        if (key == WordDb.dayKey()) return getString(R.string.today_label);

        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_YEAR, -1);
        if (key == y.get(Calendar.YEAR) * 1000 + y.get(Calendar.DAY_OF_YEAR)) {
            return getString(R.string.yesterday_label);
        }

        String d = new SimpleDateFormat("M月d日", Locale.CHINA).format(new Date(ts));
        String[] weeks = getResources().getStringArray(R.array.week_names);
        int idx = c.get(Calendar.DAY_OF_WEEK) - 1;
        if (idx < 0 || idx >= weeks.length) return d;
        return d + " " + weeks[idx];
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}

package com.cet4.cards;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.materialswitch.MaterialSwitch;

/** 首页：只放今天的数据和三个入口，其余都在菜单页 */
public class MainActivity extends AppCompatActivity {

    private MaterialSwitch swHome;
    private TextView tvHead, tvCaption, tvSwitch, tvDue, tvKnown, tvPermHint;
    private boolean binding = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Crash.install(this);
        setContentView(R.layout.activity_main);

        Notifier.ensureChannels(this);
        boolean rebuilt = WordDb.ensureReady(this);
        if (rebuilt) Diag.log(this, "词库自检：原库不可用，已重建");

        bind();
        setupListeners();
        refresh();
    }

    private void bind() {
        swHome = findViewById(R.id.swHome);
        tvHead = findViewById(R.id.tvHead);
        tvCaption = findViewById(R.id.tvCaption);
        tvSwitch = findViewById(R.id.tvSwitch);
        tvDue = findViewById(R.id.tvDue);
        tvKnown = findViewById(R.id.tvKnown);
        tvPermHint = findViewById(R.id.tvPermHint);
    }

    private void setupListeners() {
        swHome.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            if (checked) {
                if (!Perms.hasNotif(this)) {
                    setSwitch(false);
                    toast(getString(R.string.need_notif));
                    try {
                        startActivity(Perms.notifIntent(this));
                    } catch (Exception ignored) {
                    }
                    return;
                }
                if (!Perms.hasUsage(this)) {
                    setSwitch(false);
                    toast(getString(R.string.need_usage));
                    Perms.openUsage(this);
                    return;
                }
                Prefs.setEnabled(this, true);
                startMonitor();
                toast(getString(R.string.started));
            } else {
                Prefs.setEnabled(this, false);
                stopService(new Intent(this, MonitorService.class));
                toast(getString(R.string.stopped));
            }
            refresh();
        });

        findViewById(R.id.navLibrary).setOnClickListener(v -> open(LibraryActivity.class));
        findViewById(R.id.navMenu).setOnClickListener(v -> open(MenuActivity.class));
        findViewById(R.id.navStats).setOnClickListener(v -> open(StatsActivity.class));
        findViewById(R.id.cardDue).setOnClickListener(v -> open(LibraryActivity.class));
        findViewById(R.id.cardKnown).setOnClickListener(v -> open(LibraryActivity.class));
    }

    private void open(Class<?> cls) {
        try {
            startActivity(new Intent(this, cls));
        } catch (Throwable t) {
            Diag.log(this, "打开页面失败：" + t.getClass().getSimpleName());
        }
    }

    private void refresh() {
        binding = true;
        swHome.setChecked(Prefs.enabled(this));
        binding = false;

        int day = Math.max(1, WordDb.get(this).streakDays());
        tvHead.setText(getString(R.string.home_head_fmt, getString(R.string.app_name), day));

        int today = WordDb.get(this).todayCount();
        tvCaption.setText(today <= 0
                ? getString(R.string.home_caption_idle)
                : getString(R.string.home_caption_fmt, today));

        tvSwitch.setText(Prefs.enabled(this) ? R.string.home_on : R.string.home_off);

        try {
            WordDb db = WordDb.get(this);
            tvDue.setText(String.valueOf(db.countDue(Prefs.books(this), Prefs.mineOnly(this))));
            tvKnown.setText(String.valueOf(db.countStatus(3)));
        } catch (Throwable ignored) {
        }

        refreshPermHint();
    }

    /** 首页底部小字：缺权限时提醒用户，全部就绪则不显示 */
    private void refreshPermHint() {
        boolean notif = Perms.hasNotif(this);
        boolean usage = Perms.hasUsage(this);
        boolean battery = Perms.ignoringBattery(this);
        if (notif && usage && battery) {
            tvPermHint.setVisibility(android.view.View.GONE);
        } else {
            tvPermHint.setVisibility(android.view.View.VISIBLE);
            tvPermHint.setText(getString(R.string.home_perm_hint));
        }
    }

    private void setSwitch(boolean on) {
        binding = true;
        swHome.setChecked(on);
        binding = false;
    }

    private void startMonitor() {
        try {
            androidx.core.content.ContextCompat.startForegroundService(this,
                    new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_START));
        } catch (Throwable t) {
            Diag.log(this, "启动服务失败：" + t.getClass().getSimpleName());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 5000);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        super.onDestroy();
    }
}

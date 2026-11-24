package com.cet4.cards;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

/** 设置页：监控应用、权限、间隔、词书、诊断、模拟 */
public class SettingsActivity extends AppCompatActivity {

    private MaterialSwitch swSim, swOverlay, swMineOnly;
    private TextView tvApps, tvBooks;
    private TextView tvNotifState, tvUsageState, tvBatteryState, tvOverlayState, tvAlarmState, tvAccState;
    private TextView tvDiagService, tvDiagPkg, tvDiagTimer;
    private ChipGroup chipGroup;
    private Chip chip1, chip3, chip5, chip10;
    private boolean binding = false;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable diagTick = new Runnable() {
        @Override
        public void run() {
            refreshDiag();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        bind();
        setupListeners();
        refresh();
    }

    private void bind() {
        swSim = findViewById(R.id.swSim);
        swOverlay = findViewById(R.id.swOverlay);
        swMineOnly = findViewById(R.id.swMineOnly);
        tvApps = findViewById(R.id.tvApps);
        tvBooks = findViewById(R.id.tvBooks);
        tvNotifState = findViewById(R.id.tvNotifState);
        tvUsageState = findViewById(R.id.tvUsageState);
        tvBatteryState = findViewById(R.id.tvBatteryState);
        tvOverlayState = findViewById(R.id.tvOverlayState);
        tvAlarmState = findViewById(R.id.tvAlarmState);
        tvAccState = findViewById(R.id.tvAccState);
        tvDiagService = findViewById(R.id.tvDiagService);
        tvDiagPkg = findViewById(R.id.tvDiagPkg);
        tvDiagTimer = findViewById(R.id.tvDiagTimer);
        chipGroup = findViewById(R.id.chipGroup);
        chip1 = findViewById(R.id.chip1);
        chip3 = findViewById(R.id.chip3);
        chip5 = findViewById(R.id.chip5);
        chip10 = findViewById(R.id.chip10);
    }

    private void setupListeners() {
        findViewById(R.id.btnApps).setOnClickListener(v ->
                startActivity(new Intent(this, AppPickerActivity.class)));

        findViewById(R.id.btnNotif).setOnClickListener(v -> {
            if (!Perms.hasNotif(this)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
                } else {
                    startActivity(Perms.notifIntent(this));
                }
            }
        });
        findViewById(R.id.btnUsage).setOnClickListener(v -> Perms.openUsage(this));
        findViewById(R.id.btnBattery).setOnClickListener(v -> Perms.requestBattery(this));
        findViewById(R.id.btnOverlay).setOnClickListener(v -> Perms.openOverlay(this));
        findViewById(R.id.btnAlarm).setOnClickListener(v -> Perms.openExactAlarm(this));
        findViewById(R.id.btnAcc).setOnClickListener(v -> Perms.openAccessibility(this));

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (binding || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            int min = id == R.id.chip1 ? 1 : id == R.id.chip3 ? 3 : id == R.id.chip5 ? 5 : 10;
            Prefs.setInterval(this, min);
            refresh();
        });

        swMineOnly.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setMineOnly(this, checked);
        });

        findViewById(R.id.btnBooks).setOnClickListener(v -> BookPicker.show(this, this::refresh));

        findViewById(R.id.btnTest).setOnClickListener(v -> {
            if (!Perms.hasNotif(this)) {
                toast(getString(R.string.need_notif));
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
                } else {
                    startActivity(Perms.notifIntent(this));
                }
                return;
            }
            if (WordDb.get(this).total() <= 0) {
                toast(getString(R.string.word_db_empty));
                return;
            }
            MonitorService.testNow(this);
        });

        swSim.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setSimulate(this, checked);
            if (checked && !Prefs.enabled(this)) {
                Prefs.setEnabled(this, true);
                startMonitor();
            }
            Diag.log(this, checked ? "开启模拟模式" : "关闭模拟模式");
        });

        swOverlay.setOnCheckedChangeListener((b, checked) -> {
            if (binding) return;
            Prefs.setOverlay(this, checked);
            if (checked && !OverlayWord.canDraw(this)) Perms.openOverlay(this);
        });

        findViewById(R.id.btnCopyDiag).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("诊断", Diag.dump(this)));
            toast(getString(R.string.diag_copied));
        });

        findViewById(R.id.btnClearLog).setOnClickListener(v -> {
            Diag.clearLog(this);
            refreshDiag();
        });

        findViewById(R.id.btnTestLater).setOnClickListener(v -> {
            AlarmScheduler.scheduleTest(this, 30_000L);
            toast(getString(R.string.test_later_done));
        });
    }

    private void startMonitor() {
        try {
            androidx.core.content.ContextCompat.startForegroundService(this,
                    new Intent(this, MonitorService.class).setAction(MonitorService.ACTION_START));
        } catch (Throwable t) {
            Diag.log(this, "启动服务失败：" + t.getClass().getSimpleName());
        }
    }

    private void refresh() {
        binding = true;
        swSim.setChecked(Prefs.simulate(this));
        swOverlay.setChecked(Prefs.overlay(this));
        swMineOnly.setChecked(Prefs.mineOnly(this));
        int min = Prefs.interval(this);
        chip1.setChecked(min == 1);
        chip3.setChecked(min == 3);
        chip5.setChecked(min == 5);
        chip10.setChecked(min == 10);
        binding = false;

        tvApps.setText(getString(R.string.btn_apps_fmt, Prefs.targets(this).length));
        tvBooks.setText(booksSummary());

        setState(tvNotifState, Perms.hasNotif(this), true);
        setState(tvUsageState, Perms.hasUsage(this), true);
        setState(tvBatteryState, Perms.ignoringBattery(this), false);
        setState(tvOverlayState, Perms.canOverlay(this), false);
        setState(tvAlarmState, Perms.canExactAlarm(this), false);
        setState(tvAccState, Perms.accessibility(this), false);

        refreshDiag();
    }

    private void setState(TextView tv, boolean on, boolean critical) {
        tv.setText(on ? R.string.state_on : R.string.state_off);
        tv.setTextColor(ContextCompat.getColor(this,
                on ? R.color.st_known : (critical ? R.color.st_hard : R.color.st_fuzzy)));
    }

    private String booksSummary() {
        int mask = Prefs.books(this);
        StringBuilder sb = new StringBuilder();
        for (int b : Books.all()) {
            if ((mask & b) == 0) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(Books.name(b));
        }
        if (sb.length() == 0) return getString(R.string.books_none);
        int n = WordDb.get(this).countInBooks(mask);
        return getString(R.string.books_sum_fmt, sb.toString(), n);
    }

    private void refreshDiag() {
        long tick = Diag.getLong(this, "tick");
        long age = (System.currentTimeMillis() - tick) / 1000L;
        if (tick > 0 && age <= 15) {
            tvDiagService.setText(getString(R.string.diag_service_line,
                    getString(R.string.diag_running_fmt, age)));
            tvDiagService.setTextColor(ContextCompat.getColor(this, R.color.st_known));
        } else {
            tvDiagService.setText(getString(R.string.diag_service_line,
                    getString(R.string.diag_dead)));
            tvDiagService.setTextColor(ContextCompat.getColor(this, R.color.st_hard));
        }

        String pkg = Diag.get(this, "pkg");
        if (pkg.length() == 0) {
            tvDiagPkg.setText(getString(R.string.diag_pkg_line,
                    getString(R.string.diag_pkg_none)));
        } else {
            boolean in = contains(Prefs.targets(this), pkg);
            tvDiagPkg.setText(getString(R.string.diag_pkg_state, pkg,
                    getString(in ? R.string.diag_in_yes : R.string.diag_in_no)));
        }

        long remain = Diag.getLong(this, "remain");
        if (remain <= 0) {
            tvDiagTimer.setText(getString(R.string.diag_timer_line,
                    getString(R.string.diag_idle)));
        } else {
            tvDiagTimer.setText(getString(R.string.diag_timer_line,
                    getString(R.string.diag_remain_fmt, (remain + 999) / 1000)));
        }
    }

    private boolean contains(String[] arr, String v) {
        if (v == null) return false;
        for (String s : arr) {
            if (s != null && s.equals(v)) return true;
        }
        return false;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        ui.removeCallbacks(diagTick);
        ui.post(diagTick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(diagTick);
        super.onPause();
    }
}

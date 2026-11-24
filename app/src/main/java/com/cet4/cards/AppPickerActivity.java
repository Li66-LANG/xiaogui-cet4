package com.cet4.cards;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 选择在哪些应用里触发提醒 */
public class AppPickerActivity extends AppCompatActivity {

    static class AppInfo {
        String pkg;
        String label;
        Drawable icon;
    }

    private final List<AppInfo> all = new ArrayList<>();
    private final List<AppInfo> shown = new ArrayList<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private AppAdapter adapter;
    private TextInputEditText etSearch;
    private MaterialButton btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch = findViewById(R.id.etSearch);
        btnSave = findViewById(R.id.btnSave);

        for (String p : Prefs.targets(this)) {
            if (p != null && p.length() > 0) selected.add(p);
        }
        updateSaveText();

        RecyclerView rv = findViewById(R.id.rvApps);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppAdapter();
        rv.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filter(); }
        });

        btnSave.setOnClickListener(v -> {
            Prefs.setTargets(this, selected);
            Toast.makeText(this, getString(R.string.apps_saved), Toast.LENGTH_SHORT).show();
            finish();
        });

        loadApps();
    }

    private void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            List<AppInfo> list = new ArrayList<>();
            try {
                android.content.Intent main = new android.content.Intent(
                        android.content.Intent.ACTION_MAIN);
                main.addCategory(android.content.Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
                Set<String> seen = new HashSet<>();
                for (ResolveInfo ri : ris) {
                    if (ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg == null || pkg.equals(getPackageName())) continue;
                    if (!seen.add(pkg)) continue;
                    AppInfo a = new AppInfo();
                    a.pkg = pkg;
                    CharSequence label = ri.loadLabel(pm);
                    a.label = label == null ? pkg : label.toString();
                    try {
                        a.icon = ri.loadIcon(pm);
                    } catch (Exception ignored) {
                    }
                    list.add(a);
                }
            } catch (Exception ignored) {
            }

            Collections.sort(list, (x, y) -> {
                boolean sx = selected.contains(x.pkg);
                boolean sy = selected.contains(y.pkg);
                if (sx != sy) return sx ? -1 : 1;
                return x.label.compareToIgnoreCase(y.label);
            });

            runOnUiThread(() -> {
                all.clear();
                all.addAll(list);
                filter();
            });
        }).start();
    }

    private void filter() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (AppInfo a : all) {
            if (q.length() == 0
                    || a.label.toLowerCase(Locale.ROOT).contains(q)
                    || a.pkg.toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(a);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void updateSaveText() {
        btnSave.setText(getString(R.string.apps_save_fmt, selected.size()));
    }

    private class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label, pkg;
            CheckBox cb;

            VH(@NonNull View v) {
                super(v);
                icon = v.findViewById(R.id.ivIcon);
                label = v.findViewById(R.id.tvLabel);
                pkg = v.findViewById(R.id.tvPkg);
                cb = v.findViewById(R.id.cbSel);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            AppInfo a = shown.get(position);
            h.label.setText(a.label);
            h.pkg.setText(a.pkg);
            if (a.icon != null) h.icon.setImageDrawable(a.icon);
            h.cb.setOnCheckedChangeListener(null);
            h.cb.setChecked(selected.contains(a.pkg));
            h.cb.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selected.add(a.pkg);
                else selected.remove(a.pkg);
                updateSaveText();
            });
            h.itemView.setOnClickListener(v -> h.cb.toggle());
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }
}

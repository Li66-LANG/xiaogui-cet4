package com.cet4.cards;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;

/** 菜单页：六个入口，右上角一只小龟 */
public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        go(R.id.itemLibrary, LibraryActivity.class);
        go(R.id.itemStats, StatsActivity.class);
        go(R.id.itemHistory, HistoryActivity.class);
        go(R.id.itemStyle, OverlayStyleActivity.class);
        go(R.id.itemSettings, SettingsActivity.class);
        go(R.id.itemNotice, UserNoticeActivity.class);
    }

    private void go(int id, Class<?> cls) {
        findViewById(id).setOnClickListener(v -> startActivity(new Intent(this, cls)));
    }
}

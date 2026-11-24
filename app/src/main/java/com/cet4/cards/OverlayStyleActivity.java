package com.cet4.cards;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/** 悬浮字外观：字体色、背景（纯色 / 自定义图片）、描边、字号，改一眼就能预览 */
public class OverlayStyleActivity extends AppCompatActivity {

    private static final int[] FG_COLORS = {
            0xFFFFFF, 0x000000, 0xFFE14D, 0x7DE8C9, 0x8AB4F8, 0xFFB74D,
            0xFF9EC4, 0xA5D6A7, 0xFF8A65, 0xE0E0E0, 0xB39DDB, 0x00E5FF
    };

    private static final int[] BG_COLORS = {
            0x000000, 0xFFFFFF, 0x1E1E24, 0x2E5BFF, 0x12A594, 0x8B0000,
            0x1B3A2F, 0xFFF3CD, 0x3A2E4F, 0x0B3C5D, 0xF5F5F5, 0xFFD9D9
    };

    private static class Swatch {
        final View ring;
        final int color;

        Swatch(View ring, int color) {
            this.ring = ring;
            this.color = color;
        }
    }

    private final List<Swatch> fgSwatches = new ArrayList<>();
    private final List<Swatch> bgSwatches = new ArrayList<>();
    private boolean binding = false;
    private int bright = 85;

    private final ActivityResultLauncher<String> pickImage =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                if (!BgImage.save(this, uri)) {
                    Toast.makeText(this, R.string.style_bg_pick_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                // 图片当背景时默认给足不透明度，否则刚选完什么都看不到
                if (Prefs.ovBgAlpha(this) < 40) {
                    Prefs.setOvBgAlpha(this, 100);
                    syncChips();
                }
                refreshBgRow();
                refreshPreview();
                Toast.makeText(this, R.string.style_bg_picked, Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diag.log(this, "进入外观页");
        setContentView(R.layout.activity_style);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        buildSwatches(findViewById(R.id.gridFg), FG_COLORS, true);
        buildSwatches(findViewById(R.id.gridBg), BG_COLORS, false);

        findViewById(R.id.btnFgCustom).setOnClickListener(v ->
                ColorPicker.show(this, getString(R.string.style_fg), Style.fg(this), rgb -> {
                    Prefs.setOvFg(this, rgb);
                    refreshSelection();
                    refreshPreview();
                }));

        findViewById(R.id.btnBgCustom).setOnClickListener(v ->
                ColorPicker.show(this, getString(R.string.style_bg), Prefs.ovBg(this), rgb -> {
                    Prefs.setOvBg(this, rgb);
                    refreshSelection();
                    refreshPreview();
                }));

        setupChips();
        setupSliders();

        android.widget.SeekBar sb = findViewById(R.id.sbBright);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar bar, int value, boolean fromUser) {
                bright = value;
                applyBright();
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar bar) { }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        bright = sb.getProgress();
        applyBright();

        findViewById(R.id.btnBgPick).setOnClickListener(v -> pickImage.launch("image/*"));
        findViewById(R.id.btnBgRemove).setOnClickListener(v -> {
            BgImage.clear(this);
            refreshBgRow();
            refreshPreview();
        });
        findViewById(R.id.btnImgAdjust).setOnClickListener(v ->
                CropDialog.show(this, this::refreshPreview));

        findViewById(R.id.btnPreview).setOnClickListener(v -> {
            Engine.push(this, true);
            Toast.makeText(this, R.string.style_preview_sent, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnReset).setOnClickListener(v -> {
            Prefs.setOvFg(this, 0xFFFFFF);
            Prefs.setOvBg(this, 0x000000);
            Prefs.setOvBgAlpha(this, 0);
            Prefs.setOvStroke(this, 0);
            Prefs.setOvScale(this, 100);
            Prefs.setOvPos(this, 8);
            Prefs.setOvDismiss(this, 12);
            Prefs.setOvImgScale(this, 100);
            Prefs.setOvImgDx(this, 0);
            Prefs.setOvImgDy(this, 0);
            syncChips();
            refreshSelection();
            refreshBgRow();
            refreshPreview();
            Toast.makeText(this, R.string.style_reset_done, Toast.LENGTH_SHORT).show();
        });

        syncChips();
        refreshSelection();
        refreshBgRow();
        refreshPreview();
    }

    /** 背景图那一行的缩略图和状态 */
    private void refreshBgRow() {
        ImageView thumb = findViewById(R.id.ivBgThumb);
        View state = findViewById(R.id.tvBgState);
        View remove = findViewById(R.id.btnBgRemove);
        View adjust = findViewById(R.id.btnImgAdjust);

        Bitmap bmp = BgImage.has(this) ? BgImage.load(this) : null;
        if (bmp != null && !bmp.isRecycled()) {
            RoundedBitmapDrawable rd = RoundedBitmapDrawableFactory.create(getResources(), bmp);
            rd.setCornerRadius(dp(10));
            thumb.setImageDrawable(rd);
            thumb.setVisibility(View.VISIBLE);
            ((android.widget.TextView) state).setText(R.string.style_bg_set);
            remove.setVisibility(View.VISIBLE);
            adjust.setVisibility(View.VISIBLE);
        } else {
            thumb.setImageDrawable(null);
            thumb.setVisibility(View.GONE);
            ((android.widget.TextView) state).setText(R.string.style_bg_none);
            remove.setVisibility(View.GONE);
            adjust.setVisibility(View.GONE);
        }
    }

    // ---------------- 色板 ----------------

    private void buildSwatches(GridLayout grid, int[] colors, final boolean isFg) {
        grid.removeAllViews();
        int size = dp(38);
        int margin = dp(3);
        for (final int c : colors) {
            FrameLayout ring = new FrameLayout(this);
            View inner = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | c);
            if ((c & 0xFFFFFF) == 0xFFFFFF) g.setStroke(dp(1), 0xFFCFCFCF);
            inner.setBackground(g);
            ring.addView(inner, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = size;
            lp.height = size;
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(ring, lp);

            ring.setOnClickListener(v -> {
                if (isFg) Prefs.setOvFg(this, c);
                else Prefs.setOvBg(this, c);
                refreshSelection();
                refreshPreview();
            });

            (isFg ? fgSwatches : bgSwatches).add(new Swatch(ring, c));
        }
    }

    private void refreshSelection() {
        int fg = Prefs.ovFg(this) & 0xFFFFFF;
        int bg = Prefs.ovBg(this) & 0xFFFFFF;
        for (Swatch s : fgSwatches) markRing(s.ring, (s.color & 0xFFFFFF) == fg);
        for (Swatch s : bgSwatches) markRing(s.ring, (s.color & 0xFFFFFF) == bg);

        // 自定义色不在色板上，直接把当前色值写在按钮上
        com.google.android.material.button.MaterialButton bf = findViewById(R.id.btnFgCustom);
        bf.setText(getString(R.string.style_custom_fmt, String.format("#%06X", fg)));
        com.google.android.material.button.MaterialButton bb = findViewById(R.id.btnBgCustom);
        bb.setText(getString(R.string.style_custom_fmt, String.format("#%06X", bg)));
    }

    private void markRing(View ring, boolean on) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.TRANSPARENT);
        if (on) d.setStroke(dp(2), color(R.color.primary));
        ring.setBackground(d);
    }

    // ---------------- 选项 ----------------

    private void setupChips() {
        ChipGroup alpha = findViewById(R.id.chipAlpha);
        alpha.setOnCheckedStateChangeListener((g, ids) -> {
            if (binding || ids.isEmpty()) return;
            int id = ids.get(0);
            int v = id == R.id.a0 ? 0 : id == R.id.a20 ? 20
                    : id == R.id.a40 ? 40 : id == R.id.a60 ? 60 : 80;
            Prefs.setOvBgAlpha(this, v);
            refreshPreview();
        });

        ChipGroup stroke = findViewById(R.id.chipStroke);
        stroke.setOnCheckedStateChangeListener((g, ids) -> {
            if (binding || ids.isEmpty()) return;
            int id = ids.get(0);
            Prefs.setOvStroke(this, id == R.id.sBlack ? 1 : id == R.id.sWhite ? 2 : 0);
            refreshPreview();
        });
    }

    private void setupSliders() {
        SeekBar dismiss = findViewById(R.id.sbDismiss);
        dismiss.setOnSeekBarChangeListener(new Seek() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (binding) return;
                Prefs.setOvDismiss(OverlayStyleActivity.this, progress);
                updateValueLabels();
            }
        });

        SeekBar pos = findViewById(R.id.sbPos);
        pos.setOnSeekBarChangeListener(new Seek() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (binding) return;
                Prefs.setOvPos(OverlayStyleActivity.this, progress + 2);
                updateValueLabels();
            }
        });

        SeekBar size = findViewById(R.id.sbSize);
        size.setOnSeekBarChangeListener(new Seek() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (binding) return;
                Prefs.setOvScale(OverlayStyleActivity.this, progress + 60);
                updateValueLabels();
                refreshPreview();
            }
        });
    }

    private void syncSliders() {
        binding = true;
        ((SeekBar) findViewById(R.id.sbPos)).setProgress(Prefs.ovPos(this) - 2);
        ((SeekBar) findViewById(R.id.sbSize)).setProgress(Prefs.ovScale(this) - 60);
        ((SeekBar) findViewById(R.id.sbDismiss)).setProgress(Prefs.ovDismiss(this));
        binding = false;
        updateValueLabels();
    }

    private void updateValueLabels() {
        ((android.widget.TextView) findViewById(R.id.tvPosVal))
                .setText(Prefs.ovPos(this) + "%");
        ((android.widget.TextView) findViewById(R.id.tvSizeVal))
                .setText(Prefs.ovScale(this) + "%");

        int d = Prefs.ovDismiss(this);
        ((android.widget.TextView) findViewById(R.id.tvDismissVal)).setText(
                d <= 0 ? getString(R.string.style_dismiss_never)
                        : getString(R.string.style_sec_fmt, d));
    }

    private abstract static class Seek implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar sb) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar sb) {
        }
    }

    private void syncChips() {
        binding = true;
        int a = Prefs.ovBgAlpha(this);
        check(R.id.a0, a == 0);
        check(R.id.a20, a == 20);
        check(R.id.a40, a == 40);
        check(R.id.a60, a == 60);
        check(R.id.a80, a == 80);

        int s = Prefs.ovStroke(this);
        check(R.id.sAuto, s == 0);
        check(R.id.sBlack, s == 1);
        check(R.id.sWhite, s == 2);

        binding = false;
        syncSliders();
    }

    private void check(int id, boolean on) {
        Chip c = findViewById(id);
        if (c != null) c.setChecked(on);
    }

    // ---------------- 预览 ----------------

    /** 预览面板按亮度在深色和浅色之间插值；亮背景时字改成深色 + 白描边 */
    private void applyBright() {
        try {
            int dark = 0xFF14141A, light = 0xFFFBF8F0;
            float t = bright / 100f;
            int r = (int) (((dark >> 16) & 0xFF) + (((light >> 16) & 0xFF) - ((dark >> 16) & 0xFF)) * t);
            int g = (int) (((dark >> 8) & 0xFF) + (((light >> 8) & 0xFF) - ((dark >> 8) & 0xFF)) * t);
            int b = (int) ((dark & 0xFF) + ((light & 0xFF) - (dark & 0xFF)) * t);
            View pane = findViewById(R.id.panePreview);
            if (pane != null) pane.setBackgroundColor(0xFF000000 | (r << 16) | (g << 8) | b);
            ((android.widget.TextView) findViewById(R.id.tvBrightVal)).setText(bright + "%");
        } catch (Throwable ignored) {
        }
        refreshPreview();
    }

    private void refreshPreview() {
        int fg = Style.fg(this);
        int stroke = Style.strokeColor(this);
        if (bright > 55) {
            fg = 0xFF22302E;
            stroke = 0xE6FFFFFF;
        }
        float k = Prefs.ovScale(this) / 100f;

        int[] words = {R.id.tvPreviewDark, R.id.tvPreviewLight};
        for (int id : words) {
            OutlineTextView tv = findViewById(id);
            tv.setTextSizeSp(20 * k);
            tv.setBold(true);
            tv.setTextColor(fg);
            tv.setStroke(stroke, 0);
            tv.setMaxWidthPx(dp(130));
            tv.setText(getString(R.string.style_preview_word));
        }

        int[] subs = {R.id.tvPreviewDarkSub, R.id.tvPreviewLightSub};
        for (int id : subs) {
            OutlineTextView tv = findViewById(id);
            tv.setTextSizeSp(11 * k);
            tv.setTextColor(fg);
            tv.setStroke(stroke, 0);
            tv.setMaxWidthPx(dp(140));
            tv.setText(getString(R.string.style_preview_sub));
        }

        int[] boxes = {R.id.boxPreviewDark, R.id.boxPreviewLight};
        for (int id : boxes) {
            LinearLayout box = findViewById(id);
            if (Style.applyBg(this, box)) {
                box.setPadding(dp(10), dp(6), dp(10), dp(6));
            } else {
                box.setPadding(dp(6), dp(6), dp(6), dp(6));
            }
        }
    }

    private int color(int res) {
        return androidx.core.content.ContextCompat.getColor(this, res);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}

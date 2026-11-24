package com.cet4.cards;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 任意取色：色相 / 鲜艳度 / 明暗 + 直接填色值 */
public class ColorPicker {

    public interface OnPick {
        void onPick(int rgb);
    }

    public static void show(Context ctx, String title, int initialRgb, final OnPick cb) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_color, null);
        final View preview = v.findViewById(R.id.vPreview);
        final SeekBar hue = v.findViewById(R.id.sbHue);
        final SeekBar sat = v.findViewById(R.id.sbSat);
        final SeekBar val = v.findViewById(R.id.sbVal);
        final EditText hex = v.findViewById(R.id.etHex);

        float[] hsv = new float[3];
        Color.colorToHSV(0xFF000000 | (initialRgb & 0xFFFFFF), hsv);

        final boolean[] binding = {true};
        hue.setProgress((int) hsv[0]);
        sat.setProgress((int) (hsv[1] * 100));
        val.setProgress((int) (hsv[2] * 100));
        binding[0] = false;

        final Runnable sync = new Runnable() {
            @Override
            public void run() {
                int c = current(hue, sat, val);
                GradientDrawable g = new GradientDrawable();
                g.setColor(c);
                g.setCornerRadius(dp(ctx, 10));
                preview.setBackground(g);

                binding[0] = true;
                hex.setText(String.format("#%06X", c & 0xFFFFFF));
                CharSequence cs = hex.getText();
                if (cs != null) hex.setSelection(cs.length());
                binding[0] = false;
            }
        };

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!binding[0]) sync.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        };
        hue.setOnSeekBarChangeListener(l);
        sat.setOnSeekBarChangeListener(l);
        val.setOnSeekBarChangeListener(l);

        hex.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (binding[0]) return;
                String t = s.toString().trim().replace("#", "");
                if (!t.matches("[0-9a-fA-F]{6}")) return;

                float[] f = new float[3];
                Color.colorToHSV(0xFF000000 | (int) Long.parseLong(t, 16), f);
                binding[0] = true;
                hue.setProgress((int) f[0]);
                sat.setProgress((int) (f[1] * 100));
                val.setProgress((int) (f[2] * 100));
                binding[0] = false;
                sync.run();
            }
        });

        sync.run();

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(v)
                .setNegativeButton(R.string.act_cancel, null)
                .setPositiveButton(R.string.act_ok, (d, w) -> {
                    if (cb != null) cb.onPick(current(hue, sat, val) & 0xFFFFFF);
                })
                .show();
    }

    private static int current(SeekBar hue, SeekBar sat, SeekBar val) {
        return Color.HSVToColor(new float[]{
                hue.getProgress(),
                sat.getProgress() / 100f,
                val.getProgress() / 100f
        });
    }

    private static int dp(Context c, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }
}

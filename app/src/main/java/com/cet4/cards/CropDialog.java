package com.cet4.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 背景图的缩放与位置微调，里面直接预览裁剪效果 */
public class CropDialog {

    public static void show(final Context ctx, final Runnable onDone) {
        final Bitmap bmp = BgImage.load(ctx);
        if (bmp == null || bmp.isRecycled()) return;

        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_crop, null);
        final View preview = v.findViewById(R.id.vCrop);
        final SeekBar sc = v.findViewById(R.id.sbCropScale);
        final SeekBar sx = v.findViewById(R.id.sbCropX);
        final SeekBar sy = v.findViewById(R.id.sbCropY);
        final TextView val = v.findViewById(R.id.tvCropVal);

        // SeekBar 没有 min，用偏移表达区间：
        // 缩放 100~300 → 0~200；左右/上下 -50~50 → 0~100
        sc.setProgress(Prefs.ovImgScale(ctx) - 100);
        sx.setProgress(Prefs.ovImgDx(ctx) + 50);
        sy.setProgress(Prefs.ovImgDy(ctx) + 50);

        final Runnable apply = new Runnable() {
            @Override
            public void run() {
                CropDrawable cd = new CropDrawable(bmp);
                cd.setRadius(dp(ctx, 12));
                cd.setScale((sc.getProgress() + 100) / 100f);
                cd.setOffset((sx.getProgress() - 50) / 100f, (sy.getProgress() - 50) / 100f);
                preview.setBackground(cd);
                val.setText(String.format(java.util.Locale.CHINA, "%d%%   ·   %d / %d",
                        sc.getProgress() + 100, sx.getProgress() - 50, sy.getProgress() - 50));
            }
        };

        SeekBar.OnSeekBarChangeListener l = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                apply.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        };
        sc.setOnSeekBarChangeListener(l);
        sx.setOnSeekBarChangeListener(l);
        sy.setOnSeekBarChangeListener(l);

        apply.run();

        new MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.style_img_adjust)
                .setView(v)
                .setNegativeButton(R.string.act_cancel, null)
                .setPositiveButton(R.string.act_ok, (d, w) -> {
                    Prefs.setOvImgScale(ctx, sc.getProgress() + 100);
                    Prefs.setOvImgDx(ctx, sx.getProgress() - 50);
                    Prefs.setOvImgDy(ctx, sy.getProgress() - 50);
                    if (onDone != null) onDone.run();
                })
                .show();
    }

    private static int dp(Context c, int v) {
        return (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }
}

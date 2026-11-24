package com.cet4.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 悬浮窗的自定义背景图。
 * 选图后压到长边 1600px 以内存进应用私有目录，避免每次弹窗解码原图。
 */
public class BgImage {

    private static final String NAME = "overlay_bg.jpg";

    private static Bitmap cache;
    private static long cacheAt;

    public static File file(Context c) {
        return new File(c.getFilesDir(), NAME);
    }

    public static boolean has(Context c) {
        return file(c).length() > 0;
    }

    public static void clear(Context c) {
        file(c).delete();
        cache = null;
        cacheAt = 0;
    }

    /** 从相册选中的 Uri 复制进来，成功返回 true */
    public static boolean save(Context c, Uri uri) {
        InputStream in = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = c.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            BitmapFactory.decodeStream(in, null, bounds);
            in.close();

            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > 1600) sample *= 2;

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            if (bmp == null) return false;

            FileOutputStream out = new FileOutputStream(file(c));
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
            out.flush();
            out.close();
            bmp.recycle();

            cache = null;
            cacheAt = 0;
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    /** 读进来（带一层缓存，文件换了才重新解码） */
    public static synchronized Bitmap load(Context c) {
        File f = file(c);
        if (!f.exists()) return null;
        long at = f.lastModified();
        if (cache != null && !cache.isRecycled() && at == cacheAt) return cache;
        try {
            cache = BitmapFactory.decodeFile(f.getAbsolutePath());
            cacheAt = at;
            return cache;
        } catch (Throwable t) {
            return null;
        }
    }
}

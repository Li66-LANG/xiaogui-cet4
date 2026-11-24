package com.cet4.cards;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/** 通知构建：单词弹窗 + 常驻监控通知 */
public class Notifier {

    public static final String CH_WORD = "word_popup";
    public static final String CH_SERVICE = "monitor";
    public static final int ID_WORD = 2001;
    public static final int ID_SERVICE = 2000;

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationChannel word = new NotificationChannel(
                CH_WORD, c.getString(R.string.ch_word), NotificationManager.IMPORTANCE_HIGH);
        word.setDescription(c.getString(R.string.ch_word_desc));
        word.enableVibration(true);
        word.setShowBadge(true);
        nm.createNotificationChannel(word);

        NotificationChannel svc = new NotificationChannel(
                CH_SERVICE, c.getString(R.string.ch_service), NotificationManager.IMPORTANCE_LOW);
        svc.setDescription(c.getString(R.string.ch_service_desc));
        svc.setShowBadge(false);
        nm.createNotificationChannel(svc);
    }

    public static void showWord(Context c, Word w, int statusCount) {
        ensureChannels(c);

        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent piOpen = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent know = new Intent(c, WordActionReceiver.class)
                .setAction(WordActionReceiver.ACTION_RATE)
                .putExtra("id", w.id).putExtra("rating", 1);
        Intent unknown = new Intent(c, WordActionReceiver.class)
                .setAction(WordActionReceiver.ACTION_RATE)
                .putExtra("id", w.id).putExtra("rating", 0);

        PendingIntent piKnow = PendingIntent.getBroadcast(c, (int) (w.id * 2 + 1), know,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent piUnknown = PendingIntent.getBroadcast(c, (int) (w.id * 2 + 2), unknown,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String text = (w.ph == null || w.ph.length() == 0 ? "" : w.ph + "\n") + w.meaningLine();

        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CH_WORD)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(w.w)
                .setContentText(w.meaningLine())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setContentIntent(piOpen)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .addAction(0, c.getString(R.string.act_know), piKnow)
                .addAction(0, c.getString(R.string.act_unknown), piUnknown)
                .setSubText(c.getString(R.string.subtext_fmt, statusCount));

        try {
            NotificationManagerCompat.from(c).notify(ID_WORD, b.build());
            Diag.log(c, "通知已提交");
        } catch (SecurityException e) {
            Diag.log(c, "通知被拒：SecurityException（通知权限被系统限制）");
        } catch (Throwable t) {
            Diag.log(c, "通知异常：" + t.getClass().getSimpleName() + " " + t.getMessage());
        }
    }

    public static Notification serviceNotification(Context c, String title, String text) {
        ensureChannels(c);
        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stop = new Intent(c, MonitorService.class).setAction(MonitorService.ACTION_STOP);
        PendingIntent piStop = PendingIntent.getService(c, 4, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(c, CH_SERVICE)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .addAction(0, c.getString(R.string.act_stop), piStop)
                .build();
    }
}

package com.cet4.cards;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.core.app.NotificationManagerCompat;

/** 通知上的「记住了 / 还不认识」按钮 */
public class WordActionReceiver extends BroadcastReceiver {

    public static final String ACTION_RATE = "com.cet4.cards.RATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_RATE.equals(intent.getAction())) return;
        long id = intent.getLongExtra("id", -1);
        int rating = intent.getIntExtra("rating", 1);
        if (id <= 0) return;

        WordDb.get(context).rate(id, rating);
        NotificationManagerCompat.from(context).cancel(Notifier.ID_WORD);
        // 通知上已经评价过了，卡片要跟着收起，否则它到点自动消失时
        // 会再把这个词记一次「我的生词」，覆盖掉刚才的评价
        OverlayWord.dismiss();

        String msg = context.getString(rating == 1 ? R.string.toast_know : R.string.toast_unknown);
        Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }
}

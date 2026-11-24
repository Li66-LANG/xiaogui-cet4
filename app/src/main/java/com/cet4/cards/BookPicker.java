package com.cet4.cards;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/** 选择要复习的词书（多选） */
public class BookPicker {

    public static void show(Context c, Runnable onDone) {
        final int[] bits = Books.all();
        int mask = Prefs.books(c);
        final boolean[] checked = new boolean[bits.length];
        final String[] labels = new String[bits.length];
        WordDb db = WordDb.get(c);
        for (int i = 0; i < bits.length; i++) {
            checked[i] = (mask & bits[i]) != 0;
            labels[i] = Books.name(bits[i]) + "（" + db.countBook(bits[i]) + " 词）\n"
                    + Books.desc(bits[i]);
        }

        final AlertDialog dlg = new AlertDialog.Builder(c)
                .setTitle(R.string.books_dialog_title)
                .setMultiChoiceItems(labels, checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.books_ok, (DialogInterface d, int w) -> {
                    int m = 0;
                    for (int i = 0; i < bits.length; i++) {
                        if (checked[i]) m |= bits[i];
                    }
                    if (m == 0) {
                        // 一本都不选会取不到词，退回来只留真题核心词
                        m = Books.C4;
                        Toast.makeText(c, R.string.books_need_one, Toast.LENGTH_SHORT).show();
                    }
                    Prefs.setBooks(c, m);
                    if (onDone != null) onDone.run();
                })
                .setNegativeButton(R.string.books_cancel, null)
                .create();
        dlg.show();
        try {
            int sum = 0;
            for (int i = 0; i < bits.length; i++) {
                if (checked[i]) sum += db.countBook(bits[i]);
            }
            dlg.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setText(c.getString(R.string.books_ok_fmt, sum));
        } catch (Throwable ignored) {
        }
    }
}

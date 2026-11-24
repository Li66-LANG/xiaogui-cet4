package com.cet4.cards;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 推送记录列表：日期分组标题 + 每条记录 */
public class LogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onSpeak(String word);
    }

    private static final int T_HEADER = 0;
    private static final int T_ITEM = 1;

    static class Row {
        boolean header;
        String title;
        LogEntry item;
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);

    LogAdapter(Listener l) {
        listener = l;
    }

    void submit(List<Row> list) {
        rows.clear();
        if (list != null) rows.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).header ? T_HEADER : T_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == T_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_log_header, parent, false));
        }
        return new ItemVH(inf.inflate(R.layout.item_log, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row r = rows.get(position);
        if (r.header) {
            ((HeaderVH) holder).tvHeader.setText(r.title);
            return;
        }

        final LogEntry e = r.item;
        ItemVH h = (ItemVH) holder;
        h.tvTime.setText(timeFmt.format(new Date(e.ts)));
        h.tvWord.setText(e.word);
        h.tvMeaning.setText(e.meaning == null ? "" : e.meaning);

        int c = e.rated == 1 ? R.color.st_known
                : e.rated == 0 ? R.color.st_hard : R.color.st_new;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(ContextCompat.getColor(h.itemView.getContext(), c));
        h.vDot.setBackground(g);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSpeak(e.word);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView tvHeader;

        HeaderVH(@NonNull View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tvHeader);
        }
    }

    static class ItemVH extends RecyclerView.ViewHolder {
        TextView tvTime, tvWord, tvMeaning;
        View vDot;

        ItemVH(@NonNull View v) {
            super(v);
            tvTime = v.findViewById(R.id.tvTime);
            tvWord = v.findViewById(R.id.tvWord);
            tvMeaning = v.findViewById(R.id.tvMeaning);
            vDot = v.findViewById(R.id.vDot);
        }
    }
}

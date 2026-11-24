package com.cet4.cards;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class WordAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onSpeak(Word w);
    }

    private static final int TYPE_WORD = 0;
    private static final int TYPE_TAIL = 1;

    private final List<Word> data = new ArrayList<>();
    private final Listener listener;

    public WordAdapter(Listener l) {
        this.listener = l;
    }

    public void submit(List<Word> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position < data.size() ? TYPE_WORD : TYPE_TAIL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_TAIL) {
            return new TailVH(inf.inflate(R.layout.item_tail, parent, false));
        }
        return new VH(inf.inflate(R.layout.item_word, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TailVH) return;
        VH h = (VH) holder;
        Word w = data.get(position);
        h.tvWord.setText(w.w);
        h.tvPh.setText(w.ph == null ? "" : w.ph);
        h.tvMeaning.setText(w.meaningLine());

        int color;
        switch (w.status) {
            case 1: color = R.color.st_hard; break;
            case 2: color = R.color.st_fuzzy; break;
            case 3: color = R.color.st_known; break;
            default: color = R.color.st_new; break;
        }
        h.vDot.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(h.itemView.getContext(), color)));

        StringBuilder tag = new StringBuilder();
        if ((w.bm & Books.C4) != 0) tag.append(Books.name(Books.C4));
        else if ((w.bm & Books.READ) != 0) tag.append(Books.name(Books.READ));
        else if ((w.bm & Books.FREQ) != 0) tag.append(Books.name(Books.FREQ));
        else if ((w.bm & Books.RAND) != 0) tag.append(Books.name(Books.RAND));
        else if (w.mine) tag.append("我的生词");
        if (w.mine && (w.bm & Books.ALL) != 0) tag.append(" · 生词");
        h.tvBook.setText(tag.toString());
        h.tvBook.setVisibility(tag.length() == 0 ? View.GONE : View.VISIBLE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSpeak(w);
        });
    }

    @Override
    public int getItemCount() {
        return data.isEmpty() ? 0 : data.size() + 1;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvWord, tvPh, tvMeaning, tvBook;
        View vDot;

        VH(@NonNull View v) {
            super(v);
            tvWord = v.findViewById(R.id.tvWord);
            tvPh = v.findViewById(R.id.tvPh);
            tvMeaning = v.findViewById(R.id.tvMeaning);
            tvBook = v.findViewById(R.id.tvBook);
            vDot = v.findViewById(R.id.vDot);
        }
    }

    static class TailVH extends RecyclerView.ViewHolder {
        TailVH(@NonNull View v) {
            super(v);
        }
    }
}

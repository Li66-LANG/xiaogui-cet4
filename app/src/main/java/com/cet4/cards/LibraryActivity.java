package com.cet4.cards;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class LibraryActivity extends AppCompatActivity {

    private WordAdapter adapter;
    private TextInputEditText etSearch;
    private TextView tvCount, tvRange;
    private View emptyBox;
    private ChipGroup filterGroup, bookGroup;
    private RangeBar rangeBar;
    private Tts tts;
    private int filter = -1;   // -1 全部 / 0..3 状态 / 4 我的生词
    private int bookMask = 0;  // 0 = 全部词书
    private boolean binding = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diag.log(this, "进入词库页");
        setContentView(R.layout.activity_library);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearch = findViewById(R.id.etSearch);
        tvCount = findViewById(R.id.tvCount);
        tvRange = findViewById(R.id.tvRange);
        emptyBox = findViewById(R.id.emptyBox);
        filterGroup = findViewById(R.id.filterGroup);
        bookGroup = findViewById(R.id.bookGroup);
        rangeBar = findViewById(R.id.rangeBar);

        RecyclerView rv = findViewById(R.id.rvWords);
        final GridLayoutManager glm = new GridLayoutManager(this, 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int n = adapter.getItemCount();
                return position == n - 1 ? 2 : 1;
            }
        });
        rv.setLayoutManager(glm);
        adapter = new WordAdapter(w -> tts.speak(w.w));
        rv.setAdapter(adapter);

        tts = new Tts(this);

        rangeBar.setOnRange((lo, hi) -> load());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                load();
            }
        });

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (binding || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.fHard) filter = 1;
            else if (id == R.id.fFuzzy) filter = 2;
            else if (id == R.id.fKnown) filter = 3;
            else if (id == R.id.fMine) filter = 4;
            else filter = -1;
            load();
        });

        bookGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (binding || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.bC4) bookMask = Books.C4;
            else if (id == R.id.bRead) bookMask = Books.READ;
            else if (id == R.id.bFreq) bookMask = Books.FREQ;
            else if (id == R.id.bRand) bookMask = Books.RAND;
            else bookMask = 0;
            load();
        });

        load();
    }

    private void load() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString();
        int lo = rangeBar.lo();
        int hi = rangeBar.hi();
        List<Word> list = WordDb.get(this).list(q, filter, bookMask, lo, hi);
        adapter.submit(list);
        tvCount.setText(getString(R.string.count_fmt, list.size()));
        tvRange.setText(getString(R.string.range_fmt, list.size()));
        boolean empty = list.isEmpty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}

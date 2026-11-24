package com.cet4.cards;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 词库存储：首次启动时把 assets/words.json 导入 SQLite。
 *
 * 复习规则（v4 起）：
 *   - 每个词至少被弹 3 次（取词时优先 shown 小的）
 *   - 连续 3 次「记住了」才算掌握；任意一次「还不认识」把计数清零
 *   - 第一次就「还不认识」→ 我的生词；先记住后又忘 → 待巩固
 *   - 连续 3 条弹窗里不出现同一个词
 */
public class WordDb extends SQLiteOpenHelper {

    private static final String NAME = "cet4.db";
    private static final int VER = 4;
    private static WordDb inst;

    private final Context ctx;

    public static synchronized WordDb get(Context c) {
        if (inst == null) inst = new WordDb(c.getApplicationContext());
        return inst;
    }

    private WordDb(Context c) {
        super(c, NAME, null, VER);
        ctx = c;
    }

    /**
     * 启动自检：词库读不出来、或者一条词都没有，就把库删掉重建。
     */
    public static boolean ensureReady(Context c) {
        boolean broken = false;
        try {
            if (get(c).total() <= 0) broken = true;
        } catch (Throwable t) {
            broken = true;
        }
        if (!broken) return false;

        try {
            if (inst != null) inst.close();
        } catch (Throwable ignored) {
        }
        inst = null;
        try {
            c.deleteDatabase(NAME);
        } catch (Throwable ignored) {
        }
        try {
            return get(c).total() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final String SCHEMA =
            "CREATE TABLE words(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "w TEXT NOT NULL UNIQUE," +
                    "ph TEXT," +
                    "pos TEXT," +
                    "m TEXT," +
                    "bm INTEGER NOT NULL DEFAULT 0," +
                    "mine INTEGER NOT NULL DEFAULT 0," +
                    "status INTEGER NOT NULL DEFAULT 0," +
                    "shown INTEGER NOT NULL DEFAULT 0," +
                    "streak INTEGER NOT NULL DEFAULT 0," +
                    "ever_known INTEGER NOT NULL DEFAULT 0)";

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SCHEMA);
        db.execSQL("CREATE TABLE daily(day INTEGER PRIMARY KEY, sent INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_status ON words(status)");
        db.execSQL("CREATE INDEX idx_bm ON words(bm)");
        db.execSQL("CREATE INDEX idx_mine ON words(mine)");
        createLog(db);
        importFromAssets(db);
    }

    /** 推送明细表：供统计页使用（daily 只记次数，这里记到每个词） */
    private void createLog(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS push_log(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "day INTEGER NOT NULL," +
                "hour INTEGER NOT NULL," +
                "wid INTEGER," +
                "w TEXT NOT NULL," +
                "ph TEXT, pos TEXT, m TEXT," +
                "rated INTEGER NOT NULL DEFAULT -1)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_log_ts ON push_log(ts)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_log_w ON push_log(w)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_log_day ON push_log(day)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v4：词库扩成四本，words 表结构大改。重建表，把旧的掌握状态按单词搬过来。
        // 注意：这里不要再开事务——SQLiteOpenHelper 已经把 onUpgrade 包在一层事务里，
        // 嵌套事务一旦中途失败会把整次升级回滚，库就升不上去了。
        if (oldVersion < 4) {
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS words_old(" +
                        "w TEXT PRIMARY KEY, status INTEGER, mine INTEGER)");
                try {
                    db.execSQL("INSERT OR IGNORE INTO words_old(w,status,mine) " +
                            "SELECT w, status, CASE WHEN tag='mine' THEN 1 ELSE 0 END " +
                            "FROM words WHERE status>0 OR tag='mine'");
                } catch (Exception e) {
                    try {
                        db.execSQL("INSERT OR IGNORE INTO words_old(w,status,mine) " +
                                "SELECT w, status, 0 FROM words");
                    } catch (Exception ignored) {
                    }
                }
                db.execSQL("DROP TABLE IF EXISTS words");
                db.execSQL(SCHEMA);
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_status ON words(status)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_bm ON words(bm)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_mine ON words(mine)");
                importFromAssets(db);
                db.execSQL("UPDATE words SET " +
                        "status = (SELECT o.status FROM words_old o WHERE o.w = words.w), " +
                        "mine   = (SELECT o.mine   FROM words_old o WHERE o.w = words.w) " +
                        "WHERE EXISTS (SELECT 1 FROM words_old o WHERE o.w = words.w)");
                db.execSQL("UPDATE words SET mine=1, status=1 WHERE mine=1 AND status=0");
                db.execSQL("DROP TABLE IF EXISTS words_old");
            } catch (Exception ignored) {
            }
        }
        try {
            createLog(db);
        } catch (Exception ignored) {
        }
    }

    private void importFromAssets(SQLiteDatabase db) {
        InputStream in = null;
        boolean ownTx = !db.inTransaction();
        try {
            in = ctx.getAssets().open("words.json");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            JSONArray arr = new JSONArray(new String(bo.toByteArray(), "UTF-8"));

            if (ownTx) db.beginTransaction();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                ContentValues v = new ContentValues();
                v.put("w", o.optString("w"));
                v.put("ph", o.optString("ph"));
                v.put("pos", o.optString("pos"));
                v.put("m", o.optString("m"));
                v.put("bm", o.optInt("bm", 0));
                v.put("mine", o.optInt("mine", 0));
                v.put("status", o.optInt("s", 0));
                db.insertWithOnConflict("words", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
            if (ownTx) {
                db.setTransactionSuccessful();
            }
        } catch (Exception e) {
            // 导入失败不影响 App 启动，界面会显示 0 词
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            // 只有自己开的事务才能自己结束（在 onCreate/onUpgrade 里 helper 已经包了一层）
            if (ownTx) {
                try { db.endTransaction(); } catch (Exception ignored) { }
            }
        }
    }

    // ---------------- 查询 ----------------

    public int total() {
        return rawInt("SELECT COUNT(*) FROM words", null);
    }

    public int countStatus(int s) {
        return rawInt("SELECT COUNT(*) FROM words WHERE status=?", new String[]{String.valueOf(s)});
    }

    public int countBook(int book) {
        return rawInt("SELECT COUNT(*) FROM words WHERE (bm & ?) <> 0",
                new String[]{String.valueOf(book)});
    }

    public int countInBooks(int mask) {
        if (mask == 0 || mask == Books.ALL) return total();
        return rawInt("SELECT COUNT(*) FROM words WHERE (bm & ?) <> 0",
                new String[]{String.valueOf(mask)});
    }

    public int countDue(int mask, boolean mineOnly) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM words WHERE status < 3");
        if (mask != 0 && mask != Books.ALL) sql.append(" AND (bm & ").append(mask).append(") <> 0");
        if (mineOnly) sql.append(" AND mine = 1");
        return rawInt(sql.toString(), null);
    }

    public int countMine() {
        return rawInt("SELECT COUNT(*) FROM words WHERE mine = 1", null);
    }

    /**
     * 取下一个要复习的词。
     *
     * 排序 (shown >= 3) ASC, shown ASC 保证"每个词至少出现 3 次"：
     * 先把没弹够 3 次的词轮流弹完，弹够的词才进入下一轮。
     */
    public Word pickReview(int mask, boolean mineOnly, String[] recent) {
        Word w = pickInner(mask, mineOnly, recent, true);
        if (w == null) w = pickInner(mask, mineOnly, null, true);
        if (w == null) w = pickInner(mask, mineOnly, null, false);
        return w;
    }

    private Word pickInner(int mask, boolean mineOnly, String[] recent, boolean unmastered) {
        StringBuilder sql = new StringBuilder("SELECT * FROM words WHERE 1=1");
        if (unmastered) sql.append(" AND status < 3");
        if (mask != 0 && mask != Books.ALL) {
            sql.append(" AND (bm & ").append(mask).append(") <> 0");
        }
        if (mineOnly) sql.append(" AND mine = 1");
        if (recent != null) {
            for (String r : recent) {
                if (r == null || r.length() == 0) continue;
                sql.append(" AND w <> '").append(r.replace("'", "''")).append("'");
            }
        }
        sql.append(" ORDER BY (shown >= 3) ASC, shown ASC, RANDOM() LIMIT 1");
        return pick(sql.toString(), null);
    }

    public List<Word> list(String query, int filter, int bookMask) {
        return list(query, filter, bookMask, 0, 3);
    }

    /** lo/hi：连续记住次数的区间筛选（0~3，3 = 已掌握） */
    public List<Word> list(String query, int filter, int bookMask, int lo, int hi) {
        StringBuilder sql = new StringBuilder("SELECT * FROM words WHERE 1=1");
        List<String> args = new ArrayList<>();
        if (query != null && query.trim().length() > 0) {
            sql.append(" AND (w LIKE ? OR m LIKE ?)");
            String q = "%" + query.trim() + "%";
            args.add(q);
            args.add(q);
        }
        if (filter == 4) {
            sql.append(" AND mine = 1");
        } else if (filter >= 0 && filter <= 3) {
            sql.append(" AND status=").append(filter);
        }
        if (bookMask != 0 && bookMask != Books.ALL) {
            sql.append(" AND (bm & ").append(bookMask).append(") <> 0");
        }
        if (lo > 0 || hi < 3) {
            sql.append(" AND streak BETWEEN ").append(lo).append(" AND ").append(hi);
        }
        sql.append(" ORDER BY CASE status WHEN 2 THEN 0 WHEN 1 THEN 1 WHEN 0 THEN 2 ELSE 3 END, w");
        List<Word> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(sql.toString(), args.toArray(new String[0]));
            while (c.moveToNext()) out.add(read(c));
        } catch (Throwable t) {
            // 库读不出来时不能让列表页崩，返回空由界面提示
            return out;
        } finally {
            if (c != null) {
                try { c.close(); } catch (Throwable ignored) { }
            }
        }
        return out;
    }

    public Word byId(long id) {
        return pick("SELECT * FROM words WHERE id=?", new String[]{String.valueOf(id)});
    }

    public Word byWord(String w) {
        return pick("SELECT * FROM words WHERE w=?", new String[]{w});
    }

    private Word pick(String sql, String[] args) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(sql, args);
            if (c.moveToFirst()) return read(c);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private Word read(Cursor c) {
        Word w = new Word();
        w.id = c.getLong(c.getColumnIndexOrThrow("id"));
        w.w = c.getString(c.getColumnIndexOrThrow("w"));
        w.ph = c.getString(c.getColumnIndexOrThrow("ph"));
        w.pos = c.getString(c.getColumnIndexOrThrow("pos"));
        w.m = c.getString(c.getColumnIndexOrThrow("m"));
        w.bm = c.getInt(c.getColumnIndexOrThrow("bm"));
        w.status = c.getInt(c.getColumnIndexOrThrow("status"));
        w.shown = c.getInt(c.getColumnIndexOrThrow("shown"));
        w.streak = c.getInt(c.getColumnIndexOrThrow("streak"));
        w.mine = c.getInt(c.getColumnIndexOrThrow("mine")) == 1;
        return w;
    }

    private int rawInt(String sql, String[] args) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(sql, args);
            if (c.moveToFirst()) return c.getInt(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return 0;
    }

    // ---------------- 更新 ----------------

    /** 每弹一次计数 +1，用来保证"每个词至少出现 3 次" */
    public void bumpShown(long id) {
        try {
            getWritableDatabase().execSQL(
                    "UPDATE words SET shown = shown + 1 WHERE id=?", new Object[]{id});
        } catch (Exception ignored) {
        }
    }

    /**
     * 用户评价。
     *
     * rating 1 = 记住了：连续计数 +1，满 3 次标记已掌握
     * rating 0 = 还不认识：连续计数归零；从没记住过 → 我的生词；记住过 → 待巩固
     *
     * @return "mastered" / "review" / "mine" / "learning"
     */
    public String rate(long id, int rating) {
        Word w = byId(id);
        if (w == null) return "learning";
        ContentValues v = new ContentValues();
        String result;

        if (rating == 1) {
            int streak = w.streak + 1;
            v.put("streak", streak);
            v.put("ever_known", 1);
            if (streak >= 3) {
                v.put("status", 3);
                result = "mastered";
            } else {
                // 待巩固(2) 保持不变；其余仍在"学习中"
                if (w.status != 2) v.put("status", w.mine ? 1 : 0);
                result = "learning";
            }
        } else {
            v.put("streak", 0);
            if (w.status == 2 || everKnown(w)) {
                v.put("status", 2);      // 待巩固
                result = "review";
            } else {
                v.put("status", 1);      // 待攻克 = 我的生词
                v.put("mine", 1);
                result = "mine";
            }
        }
        try {
            getWritableDatabase().update("words", v, "id=?", new String[]{String.valueOf(id)});
        } catch (Exception ignored) {
        }
        markRated(w.w, rating);
        return result;
    }

    private boolean everKnown(Word w) {
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT ever_known FROM words WHERE id=?",
                    new String[]{String.valueOf(w.id)});
            if (c.moveToFirst()) return c.getInt(0) == 1;
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return false;
    }

    /** 弹窗自动消失、用户没点任何按钮 → 收进我的生词 */
    public void markIgnored(long id) {
        try {
            SQLiteDatabase d = getWritableDatabase();
            d.execSQL("UPDATE words SET mine=1, " +
                    "status = CASE WHEN status = 3 THEN 3 WHEN status = 2 THEN 2 ELSE 1 END, " +
                    "streak = 0 WHERE id=?", new Object[]{id});
        } catch (Exception ignored) {
        }
    }

    /** 直接设定掌握状态（统计页长按「标为已掌握」用） */
    public void setStatus(long id, int status) {
        ContentValues v = new ContentValues();
        v.put("status", status);
        if (status == 3) {
            v.put("streak", 3);
            v.put("ever_known", 1);
        }
        getWritableDatabase().update("words", v, "id=?", new String[]{String.valueOf(id)});
        Word w = byId(id);
        if (w != null) markRated(w.w, 1);
    }

    public static int dayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR);
    }

    public void bumpToday() {
        int day = dayKey();
        SQLiteDatabase d = getWritableDatabase();
        // 不用 UPSERT 语法：老系统（API 24~29）的 SQLite 不支持
        d.execSQL("INSERT OR IGNORE INTO daily(day, sent) VALUES(?, 0)", new Object[]{day});
        d.execSQL("UPDATE daily SET sent = sent + 1 WHERE day=?", new Object[]{day});
    }

    public int todayCount() {
        return rawInt("SELECT sent FROM daily WHERE day=?", new String[]{String.valueOf(dayKey())});
    }

    /** 有提醒记录的日期（升序），用于算连续天数 */
    public List<Integer> activeDays() {
        List<Integer> days = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery("SELECT day FROM daily WHERE sent>0 ORDER BY day DESC LIMIT 60", null);
            while (c.moveToNext()) days.add(c.getInt(0));
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return days;
    }

    // ---------------- 推送明细（统计页） ----------------

    /** 每弹一条就记一条，供统计页使用；只保留 90 天 */
    public void logPush(Word w) {
        if (w == null) return;
        try {
            long now = System.currentTimeMillis();
            ContentValues v = new ContentValues();
            v.put("ts", now);
            v.put("day", dayKey());
            v.put("hour", Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
            v.put("wid", w.id);
            v.put("w", w.w);
            v.put("ph", w.ph);
            v.put("pos", w.pos);
            v.put("m", w.m);
            v.put("rated", -1);
            SQLiteDatabase d = getWritableDatabase();
            d.insert("push_log", null, v);
            d.delete("push_log", "ts < ?",
                    new String[]{String.valueOf(now - 90L * 24 * 3600 * 1000)});
        } catch (Exception ignored) {
        }
    }

    /** 评价后回写最近一条对应记录，用来统计"推了还是不认识" */
    public void markRated(String word, int rating) {
        if (word == null) return;
        try {
            getWritableDatabase().execSQL(
                    "UPDATE push_log SET rated=? WHERE id=" +
                            "(SELECT id FROM push_log WHERE w=? ORDER BY ts DESC LIMIT 1)",
                    new Object[]{rating, word});
        } catch (Exception ignored) {
        }
    }

    /** 累计推送次数（含历史版本，来自 daily 表） */
    public int totalSent() {
        return rawInt("SELECT IFNULL(SUM(sent),0) FROM daily", null);
    }

    /** 最近 n 天的推送次数：索引 0 = 最早那天，最后一个 = 今天 */
    public int[] lastDays(int n) {
        int[] out = new int[n];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < n; i++) {
            out[n - 1 - i] = daySent(keyOf(cal));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return out;
    }

    private static int keyOf(Calendar cal) {
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
    }

    private int daySent(int day) {
        return rawInt("SELECT sent FROM daily WHERE day=?", new String[]{String.valueOf(day)});
    }

    /** 连续打卡天数：用真实日期逐日回退，跨年不会算错 */
    public int streakDays() {
        Calendar cal = Calendar.getInstance();
        if (daySent(keyOf(cal)) == 0) cal.add(Calendar.DAY_OF_YEAR, -1);
        int s = 0;
        for (int i = 0; i < 400; i++) {
            if (daySent(keyOf(cal)) > 0) {
                s++;
                cal.add(Calendar.DAY_OF_YEAR, -1);
            } else {
                break;
            }
        }
        return s;
    }

    /** 24 小时推送分布（明细表只有本版之后的，说明里会写清楚） */
    public int[] hourHistogram() {
        int[] out = new int[24];
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT hour, COUNT(*) FROM push_log GROUP BY hour", null);
            while (c.moveToNext()) {
                int h = c.getInt(0);
                if (h >= 0 && h < 24) out[h] = c.getInt(1);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** 被推得最多、但还没标记掌握的词 */
    public List<LogEntry> stubborn(int minTimes, int limit) {
        List<LogEntry> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT p.w, MAX(p.ph), MAX(p.m), COUNT(*), MAX(p.ts) " +
                            "FROM push_log p LEFT JOIN words wd ON wd.w = p.w " +
                            "WHERE IFNULL(wd.status,0) < 3 " +
                            "GROUP BY p.w HAVING COUNT(*) >= ? " +
                            "ORDER BY COUNT(*) DESC, MAX(p.ts) DESC LIMIT ?",
                    new String[]{String.valueOf(minTimes), String.valueOf(limit)});
            while (c.moveToNext()) {
                LogEntry e = new LogEntry();
                e.word = c.getString(0);
                e.ph = c.getString(1);
                e.meaning = c.getString(2);
                e.times = c.getInt(3);
                e.ts = c.getLong(4);
                out.add(e);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** 最近推送记录（倒序） */
    public List<LogEntry> recentLogs(int limit) {
        List<LogEntry> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = getReadableDatabase().rawQuery(
                    "SELECT w, ph, m, ts, rated FROM push_log ORDER BY ts DESC LIMIT ?",
                    new String[]{String.valueOf(limit)});
            while (c.moveToNext()) {
                LogEntry e = new LogEntry();
                e.word = c.getString(0);
                e.ph = c.getString(1);
                e.meaning = c.getString(2);
                e.ts = c.getLong(3);
                e.rated = c.getInt(4);
                out.add(e);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** 已推送过的不重复单词数 */
    public int pushedWordCount() {
        return rawInt("SELECT COUNT(DISTINCT w) FROM push_log", null);
    }

    /** 最近 n 天的全部推送（倒序），供按天查看使用 */
    public List<LogEntry> logsSince(int days) {
        List<LogEntry> out = new ArrayList<>();
        Cursor c = null;
        try {
            long from = System.currentTimeMillis() - days * 24L * 3600 * 1000;
            c = getReadableDatabase().rawQuery(
                    "SELECT w, ph, m, ts, rated, day FROM push_log " +
                            "WHERE ts >= ? ORDER BY ts DESC LIMIT 800",
                    new String[]{String.valueOf(from)});
            while (c.moveToNext()) {
                LogEntry e = new LogEntry();
                e.word = c.getString(0);
                e.ph = c.getString(1);
                e.meaning = c.getString(2);
                e.ts = c.getLong(3);
                e.rated = c.getInt(4);
                e.day = c.getInt(5);
                out.add(e);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }
}

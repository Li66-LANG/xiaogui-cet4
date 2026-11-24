package com.cet4.cards;

/** 一个单词条目 */
public class Word {
    public long id;
    public String w;      // 单词
    public String ph;     // 音标
    public String pos;    // 词性
    public String m;      // 释义
    public int bm;        // 所属词书位标，见 Books
    public int status;    // 0 学习中 1 待攻克(含我的生词) 2 待巩固 3 已掌握
    public int shown;     // 被弹出过多少次
    public int streak;    // 连续「记住了」的次数
    public boolean mine;  // 是否在「我的生词」里

    public String meaningLine() {
        StringBuilder sb = new StringBuilder();
        if (pos != null && pos.length() > 0) sb.append(pos).append("  ");
        sb.append(m == null ? "" : m);
        return sb.toString();
    }

    /** 「真题核心词 / 阅读难词」，用于词库列表上的一行小字 */
    public String bookLine() {
        StringBuilder sb = new StringBuilder();
        if (is(Books.C4)) sb.append(Books.name(Books.C4));
        if (is(Books.READ)) append(sb, Books.name(Books.READ));
        if (is(Books.FREQ)) append(sb, Books.name(Books.FREQ));
        if (is(Books.RAND)) append(sb, Books.name(Books.RAND));
        if (sb.length() == 0 && mine) sb.append("我的生词");
        return sb.toString();
    }

    public boolean is(int book) {
        return (bm & book) != 0;
    }

    private static void append(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append(" · ");
        sb.append(s);
    }
}

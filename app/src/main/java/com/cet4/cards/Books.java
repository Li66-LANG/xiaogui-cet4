package com.cet4.cards;

/** 四本词书：位标与显示名 */
public class Books {

    public static final int C4 = 1;     // 真题核心词
    public static final int READ = 2;   // 阅读难词
    public static final int FREQ = 4;   // 高频词汇
    public static final int RAND = 8;   // 乱序全库
    public static final int ALL = C4 | READ | FREQ | RAND;

    public static int[] all() {
        return new int[]{C4, READ, FREQ, RAND};
    }

    public static String name(int book) {
        switch (book) {
            case C4: return "真题核心词";
            case READ: return "阅读难词";
            case FREQ: return "高频词汇";
            case RAND: return "乱序全库";
            default: return "";
        }
    }

    public static String desc(int book) {
        switch (book) {
            case C4: return "四级真题卷面高频，录屏整理";
            case READ: return "历年阅读原文里的生词";
            case FREQ: return "考前速记，短释义";
            case RAND: return "四级词汇全表，带音标";
            default: return "";
        }
    }
}

package com.cet4.cards;

/** 一条推送记录（统计页展示用） */
public class LogEntry {
    public String word;
    public String ph;
    public String meaning;
    public long ts;        // 毫秒时间戳
    public int day;        // 与 WordDb.dayKey 一致，用于按天分组
    public int times;      // 被推送次数（顽固词列表用）
    public int rated = -1; // -1 未评价 0 还不认识 1 记住了
}

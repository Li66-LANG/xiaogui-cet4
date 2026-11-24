# -*- coding: utf-8 -*-
"""
把四份来源合并成一份词库 assets/words.json。

来源与位标（bm）：
  bit0 = 1  真题核心词（录屏整理，513）
  bit1 = 2  阅读难词（历年英语四级阅读中的难词汇总.doc）
  bit2 = 4  高频词汇（高频词汇.docx）
  bit3 = 8  乱序全库（大学英语四级词汇带音标-乱序版.xls）
"""
import json
import os
import re
import struct
import sys
import zipfile

import olefile
import xlrd

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(BASE, "app", "src", "main", "assets", "words.json")
# 原词库的固定基准（513 真题核心 + 38 生词），从 v1.16 的 APK 里取出，
# 不能用 ASSETS：那个文件每次生成都会被覆盖。
BASE_WORDS = os.path.join(BASE, "tools", "words_base.json")
OUT = ASSETS
QQ = r"D:\qq"

BIT_C4, BIT_READ, BIT_FREQ, BIT_RAND = 1, 2, 4, 8

POS_RE = re.compile(
    r"^(?:(?:n|v|vt|vi|adj|adv|a|ad|prep|conj|pron|num|art|int|aux|phr|pl)\.?\s*)+",
    re.I)


def clean_meaning(s):
    if not s:
        return ""
    s = s.replace("\u00a0", " ")
    # 去掉「[(+of)]」「[S1]」「【律】」这类标注
    s = re.sub(r"\[[^\]]*\]", " ", s)
    s = re.sub(r"【[^】]*】", " ", s)
    s = re.sub(r"\([^)]*\)", " ", s) if re.search(r"[\u4e00-\u9fff]", s) else s
    s = re.sub(r"\s+", " ", s)
    s = re.sub(r"^[\s,;:.\-]+", "", s).strip()
    return s


def split_pos(meaning):
    """从「n. 钟面；刻度盘」里拆出词性"""
    m = re.match(r"^((?:[a-z]{1,4}\.\s*)+)\s*(.*)$", meaning, re.I)
    if m and m.group(2):
        pos = re.sub(r"\s+", " ", m.group(1)).strip()
        return pos, m.group(2).strip()
    return "", meaning


def cap(s, n=46):
    if not s:
        return ""
    s = re.sub(r"\s+", " ", s).strip()
    if len(s) <= n:
        return s
    cut = s[:n]
    i = max(cut.rfind("；"), cut.rfind("，"), cut.rfind(";"), cut.rfind(","))
    return (s[:i] if i > n * 0.5 else cut) + "…"


# ---------------- 1. 老版 .doc ----------------

def read_doc(path):
    ole = olefile.OleFileIO(path)
    wd = ole.openstream("WordDocument").read()
    flags = struct.unpack_from("<H", wd, 0x0A)[0]
    tbl_name = "1Table" if (flags & 0x0200) else "0Table"
    fc_clx, lcb_clx = struct.unpack_from("<ii", wd, 0x01A2)
    tbl = ole.openstream(tbl_name).read()
    clx = tbl[fc_clx:fc_clx + lcb_clx]

    i = 0
    pcdt = None
    while i < len(clx):
        t = clx[i]
        if t == 0x01:
            cb = struct.unpack_from("<H", clx, i + 1)[0]
            i += 3 + cb
        elif t == 0x02:
            lcb = struct.unpack_from("<I", clx, i + 1)[0]
            pcdt = clx[i + 5:i + 5 + lcb]
            break
        else:
            break
    if not pcdt:
        return ""
    n = (len(pcdt) - 4) // 12
    cps = [struct.unpack_from("<I", pcdt, k * 4)[0] for k in range(n + 1)]
    off0 = (n + 1) * 4
    parts = []
    for k in range(n):
        fc = struct.unpack_from("<I", pcdt, off0 + k * 8 + 2)[0]
        comp = bool(fc & 0x40000000)
        fcv = fc & 0x3FFFFFFF
        ln = cps[k + 1] - cps[k]
        if comp:
            fcv //= 2
            parts.append(wd[fcv:fcv + ln].decode("cp1252", "ignore"))
        else:
            parts.append(wd[fcv:fcv + ln * 2].decode("utf-16-le", "ignore"))
    return "".join(parts)


# ---------------- 2. docx ----------------

def read_docx(path):
    z = zipfile.ZipFile(path)
    xml = z.read("word/document.xml").decode("utf-8", "ignore")
    out = []
    for para in re.split(r"</w:p>", xml):
        texts = re.findall(r"<w:t[^>]*>(.*?)</w:t>", para, re.S)
        s = re.sub(r"<[^>]+>", "", "".join(texts)).strip()
        if s:
            out.append(s)
    return out


# ---------------- 3. xls ----------------

def read_xls(path):
    book = xlrd.open_workbook(path)
    sheet = book.sheet_by_index(0)
    rows = []
    for r in range(1, sheet.nrows):
        w = str(sheet.cell_value(r, 1)).strip()
        ph = str(sheet.cell_value(r, 2)).strip()
        m = str(sheet.cell_value(r, 3)).strip()
        if w:
            rows.append((w, ph, m))
    return rows


EN_HEAD = re.compile(r"^([A-Za-z][A-Za-z'\-]*(?:\s+[A-Za-z][A-Za-z'\-]*){0,3})\s*(.*)$")

# 结尾的词性标记（n. / v. / vt. / adj. / a. ...），不能算进单词
TAIL_POS = re.compile(
    r"(?:^|\s)(?:n|v|vt|vi|adj|adv|a|ad|prep|conj|pron|num|art|int|aux|phr)\.?\s*$",
    re.I)


def parse_line(line):
    """「Premises n. 房宅;生产场所」→ (word, pos+meaning)"""
    line = line.strip()
    if not line:
        return None
    ci = -1
    for i, ch in enumerate(line):
        if "\u4e00" <= ch <= "\u9fff":
            ci = i
            break
    if ci <= 0:
        return None
    head, rest = line[:ci], line[ci:]
    # 反复剥掉结尾的词性标记："alter v. " → "alter"；"manufacture vt. /n. " → "manufacture"
    for _ in range(4):
        new = re.sub(r"[\s/]*\b(?:n|v|vt|vi|adj|adv|a|ad|prep|conj|pron|num|art|int|aux|phr)\.?\s*$",
                     "", head, flags=re.I)
        if new == head:
            break
        head = new
    word = head.strip(" .;,、/-")
    pos = line[len(word):ci].strip(" .;,、/")
    if not word:
        return None
    return word, (pos + " " + rest).strip()


def valid_word(w):
    if not w or len(w) > 32:
        return False
    # 允许词组（break down / give rise to sth），但每个词必须是纯字母
    if not re.match(r"^[A-Za-z][A-Za-z'\-]*(?: [A-Za-z][A-Za-z'\-]*){0,3}$", w):
        return False
    if len(w.replace(" ", "")) < 2:
        return False
    return True


def norm_ph(p):
    p = (p or "").strip()
    if not p:
        return ""
    p = p.replace("'", "ˈ").replace(":", "ː")
    if not p.startswith("/"):
        p = "/" + p
    if not p.endswith("/"):
        p = p + "/"
    return p


PROPER = set("""
monday tuesday wednesday thursday friday saturday sunday
january february march april may june july august september october november december
german chinese english japanese french american european african asian australian
canadian indian russian italian spanish greek roman christian muslim catholic
christmas bible shakespeare
""".split())


def norm_word(w):
    """统一大小写：Reproach → reproach；Sunday / German 这类专有名词保留首字母大写"""
    w = w.strip()
    if not w:
        return w
    if w == w.upper() and len(w) <= 4:
        return w            # AI / OK / TV
    low = w.lower()
    if low in PROPER:
        return low[0].upper() + low[1:]
    if w[0].isupper() and (len(w) == 1 or w[1:].islower()):
        return low
    return w


def main():
    bank = {}
    stat = {}
    extra_ph = {}
    ph_file = os.path.join(BASE, "tools", "phonetic_extra.json")
    if os.path.exists(ph_file):
        extra_ph = json.load(open(ph_file, encoding="utf-8"))

    def add(word, ph, meaning, bit, pos=""):
        word = norm_word(word)
        key = word.lower()
        if not valid_word(word):
            return
        meaning = clean_meaning(meaning)
        if not meaning:
            return
        e = bank.get(key)
        if e is None:
            e = {"w": word, "ph": "", "pos": "", "m": "", "bm": 0, "rank": 99}
            bank[key] = e
        e["bm"] |= bit
        # 音标：先到先得，但 c4/xls 的优先
        if ph and (not e["ph"] or bit in (BIT_C4, BIT_RAND)):
            e["ph"] = norm_ph(ph)
        # 释义优先级：c4(0) > xls(1) > freq(2) > read(3)
        rank = {BIT_C4: 0, BIT_RAND: 1, BIT_FREQ: 2, BIT_READ: 3}[bit]
        if not e["m"] or rank < e["rank"]:
            p, mm = split_pos(meaning)
            e["m"] = mm
            e["pos"] = p
            e["rank"] = rank

    # --- 原有词库（513 真题核心 + 生词）
    old = json.load(open(BASE_WORDS, encoding="utf-8"))
    old_mine = []
    for o in old:
        if o.get("t") == "mine":
            old_mine.append(o)
            continue
        add(o["w"], o.get("ph", ""), (o.get("pos", "") or "") + " " + (o.get("m", "") or ""),
            BIT_C4)
    stat["原词书"] = len(old)

    # --- 阅读难词 .doc
    doc_text = read_doc(os.path.join(QQ, "历年英语四级阅读中的难词汇总.doc"))
    n0 = len(bank)
    for line in re.split(r"[\r\n\x07]+", doc_text):
        line = line.strip()
        if not line or len(line) < 4:
            continue
        r = parse_line(line)
        if not r:
            continue
        add(r[0], "", r[1], BIT_READ)
    stat["阅读难词(新增)"] = len(bank) - n0

    # --- 高频词汇 .docx
    n0 = len(bank)
    for line in read_docx(os.path.join(QQ, "高频词汇.docx")):
        r = parse_line(line)
        if not r:
            continue
        add(r[0], "", r[1], BIT_FREQ)
    stat["高频词汇(新增)"] = len(bank) - n0

    # --- 乱序全库 .xls
    n0 = len(bank)
    for w, ph, m in read_xls(os.path.join(QQ, "大学英语四级词汇带音标-乱序版.xls")):
        add(w, ph, m, BIT_RAND)
    stat["全库(新增)"] = len(bank) - n0

    # --- 我的生词：保留，不进任何词书
    for o in old_mine:
        key = o["w"].lower()
        e = bank.get(key)
        if e is None:
            e = {"w": o["w"], "ph": o.get("ph", ""), "pos": o.get("pos", ""),
                 "m": o.get("m", ""), "bm": 0, "rank": 9, "mine": 1}
            bank[key] = e
        else:
            e["mine"] = 1
    stat["我的生词"] = len(old_mine)

    out = []
    for e in bank.values():
        ph = e["ph"] or extra_ph.get(e["w"].lower(), "")
        out.append({
            "w": e["w"],
            "ph": ph,
            "pos": e["pos"],
            "m": cap(e["m"]),
            "bm": e["bm"],
            **({"mine": 1} if e.get("mine") else {}),
        })

    # 排序：先按词书（c4 在前），再按字母
    out.sort(key=lambda x: (-(x["bm"] & 1), -(x["bm"] & 2), x["w"].lower()))

    json.dump(out, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))

    for k, v in stat.items():
        print(f"{k}: {v}")
    print(f"合计词条: {len(out)}")
    for bit, name in ((1, "真题核心词"), (2, "阅读难词"), (4, "高频词汇"), (8, "乱序全库")):
        n = sum(1 for x in out if x["bm"] & bit)
        print(f"  {name}: {n}")
    print("缺音标:", sum(1 for x in out if not x["ph"]))
    print("缺释义:", sum(1 for x in out if not x["m"]))
    print("体积: %.0f KB" % (os.path.getsize(OUT) / 1024))

    miss = [x["w"] for x in out if not x["ph"]]
    json.dump(miss, open(os.path.join(BASE, "tools", "no_phonetic.json"), "w",
                         encoding="utf-8"), ensure_ascii=False)


if __name__ == "__main__":
    main()

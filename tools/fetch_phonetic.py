# -*- coding: utf-8 -*-
"""给没有音标的词补音标：有道词典的公开接口，取 usphone/ukphone。

结果缓存到 tools/phonetic_extra.json，下次 build_books.py 会自动使用。
"""
import json
import os
import re
import ssl
import time
import urllib.parse
import urllib.request

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MISS = os.path.join(BASE, "tools", "no_phonetic.json")
OUT = os.path.join(BASE, "tools", "phonetic_extra.json")

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE


def fetch(word):
    url = "https://dict.youdao.com/jsonapi?q=" + urllib.parse.quote(word)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    try:
        with urllib.request.urlopen(req, timeout=15, context=CTX) as r:
            raw = r.read().decode("utf-8", "ignore")
    except Exception:
        return ""
    try:
        j = json.loads(raw)
    except Exception:
        return ""
    ec = j.get("ec") or {}
    ws = ec.get("word") or []
    if ws:
        w = ws[0]
        p = w.get("usphone") or w.get("ukphone") or ""
        if p:
            return "/" + p.strip().strip("/") + "/"
    # 短语有时在 simple
    s = (j.get("simple") or {}).get("query") or ""
    return ""


def main():
    words = json.load(open(MISS, encoding="utf-8"))
    done = {}
    if os.path.exists(OUT):
        done = json.load(open(OUT, encoding="utf-8"))
    todo = [w for w in words if w.lower() not in done]
    print("待补音标:", len(todo))
    ok = 0
    for i, w in enumerate(todo):
        p = fetch(w)
        if p:
            done[w.lower()] = p
            ok += 1
        if i % 25 == 0:
            print("  ...%d/%d" % (i, len(todo)), flush=True)
            json.dump(done, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
        time.sleep(0.12)
    json.dump(done, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
    print("补到:", ok, "总计缓存:", len(done))


if __name__ == "__main__":
    main()

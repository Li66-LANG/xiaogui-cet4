/**
 * 从四级知识库生成 App 词库 assets/words.json
 * 输入：原始资料/clean_wordlist.json（核心词表）、单词本.md（我的生词）
 * 输出：app/src/main/assets/words.json
 */
const fs = require('fs');
const path = require('path');

const KB = 'D:/workbuddy学习区/四级知识库';
const OUT = path.resolve(__dirname, '../app/src/main/assets/words.json');

function readText(p) {
  let b = fs.readFileSync(p);
  // 去 BOM
  if (b[0] === 0xEF && b[1] === 0xBB && b[2] === 0xBF) b = b.slice(3);
  return b.toString('utf8');
}

// 1) 核心词表
const core = JSON.parse(readText(path.join(KB, '原始资料/clean_wordlist.json')));
const map = new Map();
for (const it of core) {
  const w = String(it.w || '').trim();
  if (!w) continue;
  const key = w.toLowerCase();
  if (map.has(key)) continue;
  map.set(key, {
    w: w,
    ph: (it.ph || '').trim(),
    pos: (it.pos || '').trim(),
    m: (it.m || '').trim(),
    t: 'c4',
    s: 0
  });
}
const coreCount = map.size;

// 2) 我的生词（单词本.md 的表格）
const md = readText(path.join(KB, '单词本.md'));
const lines = md.split(/\r?\n/);
// 0 未学 / 1 待攻克 / 2 需巩固 / 3 已掌握
let section = null;
const statusOf = { '待攻克': 1, '需巩固': 2, '已掌握': 3 };
let mineCount = 0, mineNew = 0;
for (const raw of lines) {
  const line = raw.trim();
  if (line.startsWith('#')) {
    for (const k of Object.keys(statusOf)) {
      if (line.includes(k)) { section = statusOf[k]; break; }
    }
    continue;
  }
  if (section === null) continue;
  if (!line.startsWith('|')) continue;
  const cells = line.split('|').map(s => s.trim());
  if (cells.length < 4) continue;
  const first = cells[1];
  if (!first || /^-+$/.test(first) || /单词/.test(first)) continue;
  // 形如 resent /rɪˈzent/
  const m = first.match(/^([A-Za-z][A-Za-z'\-\.]*)\s*(\/[^\/]*\/)?/);
  if (!m) continue;
  const w = m[1];
  const ph = m[2] || '';
  const posMeaning = cells[2] || '';
  // 词性在前、释义在后：以第一个汉字为分界
  const cjk = posMeaning.search(/[\u4e00-\u9fff]/);
  let pos = '', meaning = posMeaning.trim();
  if (cjk > 0) {
    pos = posMeaning.slice(0, cjk).trim().replace(/\s+/g, ' ');
    meaning = posMeaning.slice(cjk).trim();
  }
  const key = w.toLowerCase();
  mineCount++;
  if (map.has(key)) {
    const e = map.get(key);
    if (ph) e.ph = ph;
    if (meaning) e.m = meaning;
    if (pos) e.pos = pos;
    e.t = 'mine';
    e.s = section;
  } else {
    map.set(key, { w: w, ph: ph, pos: pos, m: meaning, t: 'mine', s: section });
    mineNew++;
  }
}

const list = Array.from(map.values());
// 我的生词排前面（导入时同样优先）
list.sort((a, b) => (a.t === 'mine' ? 0 : 1) - (b.t === 'mine' ? 0 : 1));

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(list), 'utf8');

const byStatus = [0, 1, 2].map(s => list.filter(x => x.s === s).length);
const byTag = list.filter(x => x.t === 'mine').length;
console.log(`核心词 ${coreCount}，生词表条目 ${mineCount}（新增 ${mineNew}）`);
console.log(`合计 ${list.length} 词；状态 待攻克/需巩固/已掌握 = ${byStatus.join('/')}；我的生词 ${byTag}`);
console.log(`输出 ${OUT} (${(fs.statSync(OUT).size / 1024).toFixed(1)} KB)`);

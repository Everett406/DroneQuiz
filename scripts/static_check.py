#!/usr/bin/env python3
"""静态自检：括号平衡 + 残留/必备引用检查（发版 push 前必跑）。

用法：
    python3 scripts/static_check.py [源码目录]

不传参数时默认定位仓库内 app/src/main/java。
零第三方依赖：装有 ripgrep(rg) 时用其列文件，缺失则自动退回 os.walk；
环境变量 DQ_SC_NO_RG=1 可强制走纯 Python 路径（自测降级逻辑用）。

退出码：0 = PASS，1 = FAIL（缺失必备 API / 括号不平衡 / 目录不存在）。
"""
import os
import shutil
import subprocess
import sys

DEFAULT_BASE = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "java"))
BASE = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_BASE


def list_kt_files(base):
    if not os.path.isdir(base):
        print(f"[FAIL] 源码目录不存在: {base}")
        sys.exit(1)
    if shutil.which("rg") and os.environ.get("DQ_SC_NO_RG") != "1":
        out = subprocess.run(["rg", "--files", base, "-g", "*.kt"],
                             capture_output=True, text=True).stdout.split()
        if out:
            return out
    files = []
    for root, _dirs, names in os.walk(base):
        for n in names:
            if n.endswith(".kt"):
                files.append(os.path.join(root, n))
    return sorted(files)


FILES = list_kt_files(BASE)

fail = False
_cache = {}


def load(f):
    if f not in _cache:
        with open(f, encoding="utf-8") as fh:
            _cache[f] = fh.read()
    return _cache[f]


def grep_hits(pat):
    """按行做字面量匹配（等价 rg -n 的字面用法），返回命中行。"""
    hits = []
    for f in FILES:
        for lineno, line in enumerate(load(f).splitlines(), 1):
            if pat in line:
                hits.append(f"{f}:{lineno}: {line.strip()[:140]}")
    return hits


# 1) 括号平衡（忽略字符串/注释内容的逐字符粗检）
for f in FILES:
    src = load(f)
    depth = {"(": 0, "{": 0, "[": 0}
    pairs = {")": "(", "}": "{", "]": "["}
    i, n = 0, len(src)
    mode = None  # None | 'line' | 'block' | 'str' | 'chr'
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if mode is None:
            if c == "/" and nxt == "/":
                mode = "line"; i += 2; continue
            if c == "/" and nxt == "*":
                mode = "block"; i += 2; continue
            if c == '"':
                mode = "str"; i += 1; continue
            if c == "'":
                mode = "chr"; i += 1; continue
            if c in depth:
                depth[c] += 1
            elif c in pairs:
                depth[pairs[c]] -= 1
                if depth[pairs[c]] < 0:
                    print(f"[FAIL] {f}: 多余的 {c} @ offset {i}"); fail = True; break
        elif mode == "line":
            if c == "\n":
                mode = None
        elif mode == "block":
            if c == "*" and nxt == "/":
                mode = None; i += 2; continue
        elif mode == "str":
            if c == "\\":
                i += 2; continue
            if c == '"':
                mode = None
        elif mode == "chr":
            if c == "\\":
                i += 2; continue
            if c == "'":
                mode = None
        i += 1
    for k, v in depth.items():
        if v != 0:
            print(f"[FAIL] {f}: 括号 {k} 不平衡，差 {v}"); fail = True

# 2) 残留引用检查：历史上已删除的 API/写法再次出现时提示（多为整块重写的遗漏）
stale = [
    ("notifyHint", "SettingsScreen 已删除的 notifyHint 残留"),
    ("PeriodicWorkRequestBuilder", "Notify.kt 已移除的周期调度残留"),
    ("ExistingPeriodicWorkPolicy", "Notify.kt 已移除的策略残留"),
    ("PageInfo", "历史 CI 陷阱 PageInfo.page 残留"),
    ("calculateTargetValue", "新版 Compose 已移除的 API"),
]
for pat, desc in stale:
    hits = grep_hits(pat)
    if hits:
        print(f"[WARN] {desc}:")
        for h in hits[:6]:
            print("   ", h)

# 3) 关键 API 必须存在：防止整函数/整特性被误删而 CI 才发现
must = [
    ("practiceTsSince", "Dao 新查询"),
    ("habitStartHours", "Repo 习惯时刻"),
    ("onPostFling", "Bounce v5 兜底"),
    ("scrolledFromTopPx", "柔化连续渐显"),
    ("clipToBounds", "Bounce 过冲裁剪"),
    ("remainingBottomPx", "网格底部剩余像素"),
    ("IntrinsicSize.Min", "双卡等高"),
    ("wallScrim", "壁纸主题纱"),
    ("practiceSessionRandom", "刷题会话随机槽"),
    ("examDeleteQuota", "模考删除周限额"),
    ("addUsageMs", "打赏使用时长累计"),
    ("_new_exam_records", "MIGRATION_1_2 重建表模式(v2.8.1 修启动崩)"),
    ("index_exam_records_startedAt", "迁移补建 startedAt 索引(Room 校验必需)"),
    ("q.answers", "内置题库播种支持 multi answers 数组(修示例题库不出现)"),
    ("last7DaysByBank", "今日/近7天统计按题库隔离"),
    ("GlassAnchorMenu", "首页题库切换玻璃锚点菜单"),
    ("shareTextFile", "CSV 模板系统分享(FileProvider)"),
    ("imePadding", "键盘避让(刷题页/导入弹窗)"),
]
for pat, desc in must:
    if not grep_hits(pat):
        print(f"[FAIL] 未找到 {desc}（{pat}）"); fail = True

# 3.5) 迁移 SQL 禁止模式：NOT NULL 列带 DEFAULT 会与实体(未声明 defaultValue)校验不匹配，
#      启动即崩（v2.8.0(23) 已踩坑）。非空新列必须走重建表模式。
for f in FILES:
    if os.sep + "db" + os.sep in f or f.endswith(os.sep + "AppDatabase.kt"):
        for lineno, line in enumerate(load(f).splitlines(), 1):
            if "NOT NULL DEFAULT" in line:
                print(f"[FAIL] 迁移 SQL 禁止 NOT NULL DEFAULT（实体无 defaultValue 必崩）: {f}:{lineno}")
                fail = True

print("PASS" if not fail else "STATIC CHECK FAILED")
sys.exit(1 if fail else 0)

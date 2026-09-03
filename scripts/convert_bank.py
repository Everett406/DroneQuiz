#!/usr/bin/env python3
"""把题库 CSV 转换为 APP assets/questions.json。

用法:
    python3 convert_bank.py <csv路径> [-o 输出json路径]

默认输出到仓库内 app/src/main/assets/questions.json（在仓库根目录执行时）。
自动识别列名（题干/选项A-H/答案/解析/类型/分类/题号），多编码尝试，
答案归一化（字母/对错/数字序号）。

⚠️ 替换题库后务必检查输出 JSON 的 "version" 字段：必须比上一版递增，
   否则存量用户启动时不会重导入新题库（详见 README「题库版本机制」）。
"""
import argparse
import csv
import json
import re
import sys
from pathlib import Path

DEFAULT_OUT = Path("app/src/main/assets/questions.json")

# 列名候选（全部小写化匹配）
Q_KEYS = {"题干", "题目", "问题", "question", "题库", "试题内容"}
A_KEYS = {"答案", "正确答案", "标准答案", "answer", "答案选项"}
E_KEYS = {"解析", "答案解析", "解释", "说明", "explanation", "解析说明"}
T_KEYS = {"类型", "题型", "题目类型", "type"}
C_KEYS = {"分类", "类别", "章节", "知识模块", "科目", "category", "模块"}
N_KEYS = {"题号", "序号", "编号", "id", "no", "number"}
# 选项列：选项A / A / A. / A： 等
OPT_PAT = re.compile(r"^(?:选项)?\s*([A-Ha-h])\s*[.．:：、]?\s*$")


def read_csv(path: Path):
    for enc in ("utf-8-sig", "utf-8", "gb18030", "gbk"):
        try:
            with open(path, newline="", encoding=enc) as f:
                rows = list(csv.reader(f))
            print(f"[i] 编码 {enc}，共 {len(rows)} 行")
            return rows
        except UnicodeDecodeError:
            continue
    raise SystemExit("无法识别编码")


def norm(s):
    return re.sub(r"\s+", "", (s or "").strip()).lower()


def find_col(headers, keys):
    for i, h in enumerate(headers):
        if norm(h) in keys:
            return i
    return None


def parse_answer(ans: str, n_opts: int, is_judge: bool):
    a = (ans or "").strip()
    if not a:
        return None
    # 字母答案（可能多字母，取第一）
    m = re.match(r"^\(?([A-Ha-h])\)?", a)
    if m:
        idx = ord(m.group(1).upper()) - 65
        return idx if 0 <= idx < n_opts else None
    # 对/错、正确/错误
    if a in ("对", "正确", "是", "T", "Y", "√", "true", "True"):
        return 0
    if a in ("错", "错误", "否", "F", "N", "×", "false", "False"):
        return 1
    # 数字序号（1 起）
    m = re.match(r"^(\d+)$", a)
    if m:
        idx = int(m.group(1)) - 1
        return idx if 0 <= idx < n_opts else None
    return None


def main():
    ap = argparse.ArgumentParser(description="题库 CSV -> questions.json")
    ap.add_argument("csv", help="题库 CSV 路径")
    ap.add_argument("-o", "--out", default=str(DEFAULT_OUT), help="输出 JSON 路径")
    ap.add_argument("-v", "--version", type=int, default=None,
                    help="题库版本号（不传则沿用输出文件现有 version，文件不存在时为 2）")
    args = ap.parse_args()

    src = Path(args.csv)
    rows = read_csv(src)
    headers = [h.strip() for h in rows[0]]
    print("[i] 列头:", headers)

    qi = find_col(headers, Q_KEYS)
    ai = find_col(headers, A_KEYS)
    ei = find_col(headers, E_KEYS)
    ti = find_col(headers, T_KEYS)
    ci = find_col(headers, C_KEYS)
    ni = find_col(headers, N_KEYS)
    # 选项列
    opt_cols = {}  # 字母 -> 列号
    for i, h in enumerate(headers):
        m = OPT_PAT.match(h.strip())
        if m:
            opt_cols[m.group(1).upper()] = i
    opt_cols = dict(sorted(opt_cols.items()))
    if qi is None or ai is None:
        raise SystemExit(f"未识别题干/答案列: headers={headers}")
    if not opt_cols:
        raise SystemExit(f"未识别选项列: headers={headers}")
    letters = list(opt_cols.keys())
    print(f"[i] 选项列 {letters} 题干={qi} 答案={ai} 解析={ei} 类型={ti} 分类={ci} 题号={ni}")

    questions = []
    errors = []
    seen_ids = set()
    for row_no, row in enumerate(rows[1:], start=2):
        if not row or not norm(row[qi] if qi < len(row) else ""):
            continue
        get = lambda i: (row[i].strip() if i is not None and i < len(row) else "")
        qtext = get(qi)
        raw_opts = [get(opt_cols[L]) for L in letters]
        opts = [o for o in raw_opts if o]
        ttype = norm(get(ti))
        is_judge = ("判断" in ttype) or (ttype in ("truefalse", "tf")) or (
            not ttype and len(opts) == 2 and all(o in ("正确", "错误", "对", "错") for o in opts)
        )
        if is_judge:
            opts = ["正确", "错误"]
        ans = parse_answer(get(ai), len(opts), is_judge)
        if ans is None:
            errors.append(f"行{row_no}: 答案无法解析『{get(ai)}』")
            continue
        # 分类
        cat = get(ci) or "未分类"
        # 类型串
        qtype = "judge" if is_judge else "single"
        # id：题号优先，否则基于题干稳定哈希
        if ni is not None and get(ni).isdigit():
            qid = int(get(ni))
        else:
            qid = 0
            for ch in qtext:
                qid = (31 * qid + ord(ch)) & 0x7FFFFFFFFFFFFFFF
            qid = abs(qid) or row_no
        if qid in seen_ids:
            qid = (qid * 31 + row_no) & 0x7FFFFFFFFFFFFFFF
        seen_ids.add(qid)
        questions.append({
            "id": qid,
            "category": cat,
            "type": qtype,
            "question": qtext,
            "options": opts,
            "answer": ans,
            "explanation": get(ei),
        })

    print(f"[i] 解析成功 {len(questions)} 题，失败 {len(errors)} 题")
    for e in errors[:10]:
        print("  !", e)
    singles = sum(1 for q in questions if q["type"] == "single")
    judges = len(questions) - singles
    cats = {}
    for q in questions:
        cats[q["category"]] = cats.get(q["category"], 0) + 1
    print(f"[i] 单选 {singles} / 判断 {judges}；分类 {len(cats)} 个:")
    for k, v in sorted(cats.items(), key=lambda x: -x[1])[:15]:
        print(f"    {k}: {v}")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    # 版本号：显式指定 > 沿用现有文件 > 默认 2
    version = args.version
    if version is None:
        if out.exists():
            try:
                version = json.loads(out.read_text(encoding="utf-8")).get("version", 2)
            except Exception:
                version = 2
        else:
            version = 2
    out.write_text(
        json.dumps({"version": version, "questions": questions}, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    print(f"[OK] 已写入 {out}（{out.stat().st_size / 1024:.0f} KB，version={version}）")
    if args.version is None and version == 2 and out.exists():
        print("[!] 提示：替换题库时请用 -v/--version 显式递增版本号，否则存量用户不会重导入。")


if __name__ == "__main__":
    main()

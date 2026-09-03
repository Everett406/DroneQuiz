#!/usr/bin/env python3
"""生成 DroneQuiz 固定签名 keystore，并写入 GitHub Actions Secrets。

用法（在仓库根目录或任意位置执行）:
    export GITHUB_TOKEN=<你的GitHub Token>   # 具备 repo / secrets 写权限
    pip install pynacl                 # 依赖
    python3 setup_signing.py

可配置环境变量:
    GITHUB_TOKEN       GitHub 访问令牌（必填，不落盘、不硬编码）
    DQ_GITHUB_REPO     目标仓库，默认 Everett406/DroneQuiz
    DQ_KEYSTORE_DIR    keystore 本地保存目录，默认 ./keystore（仓库外请自行指定）
    DQ_KEYSTORE_FILE   已有 keystore 文件路径（存在则复用，不重新生成）

行为:
    1. 密码不存在则随机生成（openssl rand -hex 16），存入 <keystore目录>/pass.txt
    2. keystore 不存在则用 keytool 生成（RSA 2048 / 30 年 / alias dronequiz）
    3. keystore 校验后 base64 编码，经仓库公钥 sealed box 加密，写入 Secrets:
       DQ_KEYSTORE_B64 / DQ_KS_STORE_PASS
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

from nacl import encoding, public

PAT = os.environ.get("GITHUB_TOKEN", "")
REPO = os.environ.get("DQ_GITHUB_REPO", "Everett406/DroneQuiz")
KS_DIR = os.environ.get("DQ_KEYSTORE_DIR", os.path.join(os.getcwd(), "keystore"))
KS_FILE = os.environ.get("DQ_KEYSTORE_FILE", os.path.join(KS_DIR, "dronequiz.keystore"))
ALIAS = "dronequiz"
PASS_FILE = os.path.join(KS_DIR, "pass.txt")

if not PAT:
    sys.exit("[!] 请先 export GITHUB_TOKEN=<你的GitHub Token>")


def sh(cmd, **kw):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        print("CMD FAIL:", cmd, "\nSTDERR:", r.stderr[:2000])
        sys.exit(1)
    return r.stdout.strip()


def api(method, path, payload=None):
    url = f"https://api.github.com{path}"
    data = None
    headers = {
        "Authorization": f"Bearer {PAT}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "dronequiz-setup",
    }
    body = None
    if payload is not None:
        body = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            txt = resp.read().decode()
            return resp.status, (txt and json.loads(txt) or {})
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:500]


def main():
    os.makedirs(KS_DIR, exist_ok=True)

    # 1. 密码（已存在则复用，保证幂等）
    if os.path.exists(PASS_FILE):
        password = open(PASS_FILE).read().strip()
        print("[i] 复用已有密码")
    else:
        password = sh("openssl rand -hex 16")
        open(PASS_FILE, "w").write(password)
        print("[i] 已生成新密码")

    # 2. keystore（已存在则复用）
    if not os.path.exists(KS_FILE):
        sh(
            f'keytool -genkeypair -v -keystore "{KS_FILE}" -alias {ALIAS} '
            f"-keyalg RSA -keysize 2048 -validity 10950 "
            f"-storepass {password} -keypass {password} "
            f'-dname "CN=DroneQuiz, OU=Dev, O=DroneQuiz, L=Beijing, ST=Beijing, C=CN"'
        )
        print("[i] 已生成新 keystore")
    else:
        print("[i] 复用已有 keystore")

    # 校验
    out = sh(f'keytool -list -keystore "{KS_FILE}" -storepass {password} -alias {ALIAS}')
    print("[i] keystore 校验:", out.splitlines()[0])

    # 3. GitHub 仓库公钥
    status, pk = api("GET", f"/repos/{REPO}/actions/secrets/public-key")
    if status != 200:
        print("获取公钥失败:", status, pk)
        sys.exit(1)
    print(f"[i] 仓库公钥 key_id = {pk['key_id']}")

    pk_obj = public.PublicKey(pk["key"].encode(), encoding.Base64Encoder())
    sealed = public.SealedBox(pk_obj)

    ks_b64 = base64.b64encode(open(KS_FILE, "rb").read()).decode()

    secrets = {
        "DQ_KEYSTORE_B64": ks_b64,
        "DQ_KS_STORE_PASS": password,
    }
    for name, raw in secrets.items():
        enc = sealed.encrypt(raw.encode())
        status, resp = api(
            "PUT",
            f"/repos/{REPO}/actions/secrets/{name}",
            {"encrypted_value": base64.b64encode(enc).decode(), "key_id": pk["key_id"]},
        )
        print(f"[{'OK' if status in (201, 204) else 'FAIL'}] secret {name}: HTTP {status} {resp if status not in (201, 204) else ''}")

    print("[DONE] keystore 与 secrets 就绪")
    print(f"[!!] 请立即异地备份 {KS_FILE} 与密码（丢失将无法覆盖安装升级）")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make_proof.py — 抽样端到端验证的夹具生成器

从真实 dist 里抽几份"代码"和"二进制"文件：
  - 代码(index.html, noname.js) -> proof/web
  - 二进制(极略音频/立绘 + 核心音频/图片) -> proof/pack.zip
然后把 pack.zip 解压进 proof/web（模拟资源包下载解压到同目录），
最后起 proto_server 验证 4 类 URL 都能从 localhost 200 返回。

用法: python3 scripts/make_proof.py
"""
import json
import os
import shutil
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
DIST = os.path.normpath(os.path.join(HERE, "..", "..", "noname-engine", "dist"))
PROOF = os.path.join(HERE, "..", "build", "proof")

# (dist 内相对路径, 归类) 抽样清单
SAMPLES = [
    ("index.html", "web"),
    ("noname.js", "web"),
    ("extension/极略/audio/skill/jlsgsk_caorui.mp3", "pack"),   # 极略音频
    ("extension/极略/image/character/jlsgsk_baosanniang.jpg", "pack"),  # 极略立绘
    ("audio/background/aozhan_chaoming.mp3", "pack"),            # 核心音频
    ("image/card/attackRange.png", "pack"),                      # 核心图片
]


def main():
    if not os.path.isdir(DIST):
        raise SystemExit(f"dist 不存在: {DIST}")
    web_dir = os.path.join(PROOF, "web")
    if os.path.exists(PROOF):
        shutil.rmtree(PROOF)
    os.makedirs(web_dir, exist_ok=True)

    pack_files = []
    for rel, kind in SAMPLES:
        src = os.path.join(DIST, rel)
        if not os.path.isfile(src):
            print(f"[warn] 缺失: {rel}")
            continue
        if kind == "web":
            dst = os.path.join(web_dir, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copy2(src, dst)
            print(f"[web ] {rel}")
        else:
            pack_files.append(rel)
            print(f"[pack] {rel}")

    # 打包二进制（ZIP_STORED，模拟资源包）
    zip_path = os.path.join(PROOF, "assets.zip")
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_STORED) as zf:
        for rel in pack_files:
            zf.write(os.path.join(DIST, rel), rel)

    # 解压到 web 根（模拟首次下载后解压叠加）
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(web_dir)

    # 写 manifest
    with open(os.path.join(PROOF, "manifest.json"), "w", encoding="utf-8") as mf:
        json.dump({"version": "proof", "packFile": "assets.zip",
                   "packFiles": len(pack_files)}, mf, ensure_ascii=False, indent=2)

    print(f"\n[done] proof 根目录: {web_dir}")
    print("下一步: python3 scripts/proto_server.py build/proof/web 8099")
    print("然后另开终端 curl 验证。")


if __name__ == "__main__":
    main()

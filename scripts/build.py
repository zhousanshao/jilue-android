#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
build.py — 三国杀(极略) Android 资源拆分工具

把网页引擎构建产物 dist 拆成两份：
  1) WEB  (代码/逻辑) -> app/src/main/assets/web   -> 打进 APK（很小）
  2) PACK (二进制资源) -> assets.zip + manifest.json -> 上传 GitHub Release（很大）

原理：APK 内用本地 HTTP 服务托管 web/ ，资源包下载解压到同一目录后，
所有图片/音频以相对路径（lib.assetURL 默认空）自然解析，无需改引擎代码。

用法：
  python3 scripts/build.py --dist ../noname-engine/dist \
      --web-out app/src/main/assets/web \
      --pack-out build/pack \
      --version 1.0.0

调试抽样（只装前 N 个二进制文件，便于快速验证）：
  python3 scripts/build.py --dist ../noname-engine/dist \
      --web-out build/sample/web --pack-out build/sample/pack --version test --sample 200
"""
import argparse
import json
import os
import shutil
import sys
import zipfile

# 视为“代码/逻辑”，进入 APK 的文件类型（体积小、需本地执行）
CODE_EXT = {
    ".html", ".htm", ".js", ".mjs", ".cjs", ".jsx", ".ts", ".tsx",
    ".json", ".css", ".map", ".txt", ".md", ".svg", ".xml", ".webmanifest",
}


def is_code(path: str) -> bool:
    return os.path.splitext(path)[1].lower() in CODE_EXT


def iter_files(dist: str):
    for root, _dirs, files in os.walk(dist):
        for name in files:
            full = os.path.join(root, name)
            rel = os.path.relpath(full, dist)
            yield full, rel


def build(dist, web_out, pack_out, version, sample_max=None):
    dist = os.path.abspath(dist)
    web_out = os.path.abspath(web_out)
    pack_out = os.path.abspath(pack_out)

    if not os.path.isdir(dist):
        sys.exit(f"[build] dist 不存在: {dist}")

    # 清空 web 输出
    if os.path.exists(web_out):
        shutil.rmtree(web_out)
    os.makedirs(web_out, exist_ok=True)
    os.makedirs(pack_out, exist_ok=True)

    pack_path = os.path.join(pack_out, "assets.zip")
    manifest_path = os.path.join(pack_out, "manifest.json")

    code_files = 0
    code_bytes = 0
    pack_files = 0
    pack_bytes = 0
    sampled = 0

    # 二进制已压缩(mp3/png/woff...)无需再压，用 ZIP_STORED 提速
    with zipfile.ZipFile(pack_path, "w", zipfile.ZIP_STORED) as zf:
        for full, rel in iter_files(dist):
            if is_code(full):
                dst = os.path.join(web_out, rel)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(full, dst)
                code_files += 1
                code_bytes += os.path.getsize(full)
            else:
                if sample_max is not None and sampled >= sample_max:
                    continue
                zf.write(full, rel)
                pack_files += 1
                pack_bytes += os.path.getsize(full)
                sampled += 1

    manifest = {
        "version": version,
        "packFile": "assets.zip",
        "packFiles": pack_files,
        "packBytes": pack_bytes,
        "codeFiles": code_files,
        "codeBytes": code_bytes,
    }
    with open(manifest_path, "w", encoding="utf-8") as mf:
        json.dump(manifest, mf, ensure_ascii=False, indent=2)

    print(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(f"[build] web  -> {web_out}  ({code_files} 文件, {code_bytes/1024/1024:.1f} MB)")
    print(f"[build] pack -> {pack_path}  ({pack_files} 文件, {pack_bytes/1024/1024/1024:.2f} GB)")
    return manifest


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dist", default="../noname-engine/dist", help="网页引擎 dist 目录")
    ap.add_argument("--web-out", default="app/src/main/assets/web", help="WEB(代码)输出目录")
    ap.add_argument("--pack-out", default="build/pack", help="PACK(资源包)输出目录")
    ap.add_argument("--version", default="1.0.0", help="资源包版本号(与Release tag一致)")
    ap.add_argument("--sample", type=int, default=None,
                    help="调试用：最多打包 N 个二进制文件，其余跳过")
    args = ap.parse_args()
    build(args.dist, args.web_out, args.pack_out, args.version, args.sample)


if __name__ == "__main__":
    main()


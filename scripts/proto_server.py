#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
proto_server.py — 桌面端模拟 Android GameServer（NanoHTTPD 等价逻辑）

仅用于本地验证：以某个目录为根，用 http 提供文件，正确处理
URL 编码(中文路径)、阻止目录穿越。引擎以相对路径(lib.assetURL="")
请求资源，全部从这里返回，证明"本地HTTP托管+资源包解压同目录"可行。

用法: python3 scripts/proto_server.py <root_dir> [port]
"""
import os
import sys
import urllib.parse
import http.server
import socketserver


def make_handler(root):
    class Handler(http.server.SimpleHTTPRequestHandler):
        def translate_path(self, path):
            # 解码 + 去 query/fragment + 防穿越
            path = urllib.parse.unquote(path.split("?", 1)[0].split("#", 1)[0])
            path = path.lstrip("/")
            norm = os.path.normpath(path)
            if norm.startswith("..") or os.path.isabs(norm):
                norm = ""
            return os.path.join(root, norm)

        def end_headers(self):
            self.send_header("Cache-Control", "no-store")
            super().end_headers()

        def log_message(self, *args):
            pass

    return Handler


def main():
    root = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.getcwd()
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8099
    handler = make_handler(root)
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", port), handler) as httpd:
        print(f"[proto] serving {root} on http://127.0.0.1:{port}")
        httpd.serve_forever()


if __name__ == "__main__":
    main()

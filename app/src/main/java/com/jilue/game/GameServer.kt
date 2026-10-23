package com.jilue.game

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream

/**
 * 极简内嵌 HTTP 服务：以 gameRoot(web 目录) 为根提供文件。
 * 引擎以相对路径(lib.assetURL 默认空)请求图片/音频，全部从这里返回。
 * localhost 被视为安全上下文，IndexedDB / ES Module 均可用。
 */
class GameServer(private val root: File, port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val path = sanitize(session.uri)
        val file = File(root, path)
        return if (file.exists() && file.isFile) {
            newFixedLengthResponse(
                Response.Status.OK,
                mimeOf(file),
                FileInputStream(file),
                file.length()
            )
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain; charset=utf-8",
                "404 Not Found: $path"
            )
        }
    }

    private fun sanitize(uri: String): String {
        val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
            .split("?")[0].split("#")[0]
        val norm = File(decoded).toPath().normalize().toString()
            .replace('\\', '/').trimStart('/')
        // 阻止目录穿越
        return if (norm.startsWith("..")) "" else norm
    }

    private fun mimeOf(f: File): String {
        return when (f.extension.lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "js", "mjs", "cjs" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "mp4", "webm" -> "video/mp4"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf", "otf" -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}

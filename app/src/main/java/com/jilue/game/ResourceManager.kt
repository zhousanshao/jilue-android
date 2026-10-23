package com.jilue.game

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 资源管理：
 *  1) 首次启动把 APK 内 assets/web（引擎+逻辑）拷贝到可写目录；
 *  2) 校验资源包版本，若缺失/过期则从 GitHub Release 下载 assets.zip 并解压到同目录；
 *  3) 之后所有图片/音频以相对路径由本地 HTTP 服务返回，无需改动引擎代码。
 *
 * 资源包来源优先级：设置里填写的「自定义地址」> GitHub Release(latest)。
 */
object ResourceManager {
    private const val TAG = "JilueRes"
    const val ASSET_ZIP = "assets.zip"
    const val MANIFEST = "manifest.json"

    fun gameDir(ctx: Context): File = File(ctx.getExternalFilesDir(null), "jilue").apply { mkdirs() }
    fun webDir(ctx: Context): File = File(gameDir(ctx), "web").apply { mkdirs() }
    private fun versionFile(ctx: Context): File = File(gameDir(ctx), ".asset_version")

    fun localVersion(ctx: Context): String? =
        if (versionFile(ctx).exists()) versionFile(ctx).readText().trim().ifEmpty { null } else null

    /** 把 APK 内 assets/web 拷到可写目录（首次或目录为空时） */
    fun stageBundledWeb(ctx: Context) {
        val web = webDir(ctx)
        if (web.list()?.isNotEmpty() == true) return
        copyAssets(ctx.assets, "web", web)
    }

    private fun copyAssets(am: AssetManager, src: String, dst: File) {
        val entries = am.list(src) ?: emptyArray()
        if (entries.isEmpty()) {
            // 是文件
            try {
                am.open(src).use { ins ->
                    FileOutputStream(File(dst, File(src).name)).use { ins.copyTo(it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "copyAssets file fail: $src", e)
            }
            return
        }
        for (name in entries) {
            val childSrc = if (src.isEmpty()) name else "$src/$name"
            val childDst = File(dst, name)
            if ((am.list(childSrc) ?: emptyArray()).isNotEmpty()) {
                childDst.mkdirs()
                copyAssets(am, childSrc, childDst)
            } else {
                am.open(childSrc).use { ins ->
                    FileOutputStream(childDst).use { ins.copyTo(it) }
                }
            }
        }
    }

    /**
     * 确保资源就绪：拷贝 web + （必要时）下载解压资源包。
     * @param onProgress percent, downloadedBytes, totalBytes(-1 表示未知)
     * @return 是否成功
     */
    suspend fun ensureResources(
        ctx: Context,
        baseUrl: String?,
        owner: String,
        repo: String,
        onProgress: (Int, Long, Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        stageBundledWeb(ctx)

        val remoteVersion = remoteVersion(baseUrl, owner, repo) ?: "latest"
        val local = localVersion(ctx)
        if (local == remoteVersion) {
            Log.i(TAG, "资源已是最新: $remoteVersion")
            return@withContext true
        }

        val zipUrl = resolveUrl(baseUrl, owner, repo, ASSET_ZIP)
        val zipFile = File(gameDir(ctx), ASSET_ZIP)
        Log.i(TAG, "下载资源包: $zipUrl")
        downloadFile(zipUrl, zipFile, onProgress)
        extractZip(zipFile, webDir(ctx))
        versionFile(ctx).writeText(remoteVersion)
        Log.i(TAG, "资源包就绪: $remoteVersion")
        true
    }

    /** 清除已下载资源包（保留 APK 内 web），下次启动会重新下载 */
    fun clearCache(ctx: Context) {
        val g = gameDir(ctx)
        g.listFiles()?.forEach { f ->
            if (f.name != "web" && f.name != ".asset_version") f.deleteRecursively()
        }
        versionFile(ctx).delete()
    }

    private fun resolveUrl(baseUrl: String?, owner: String, repo: String, file: String): String {
        return if (!baseUrl.isNullOrBlank()) {
            "${baseUrl.trimEnd('/')}/$file"
        } else {
            "https://github.com/$owner/$repo/releases/latest/download/$file"
        }
    }

    private fun remoteVersion(baseUrl: String?, owner: String, repo: String): String? {
        return try {
            val url = URL(resolveUrl(baseUrl, owner, repo, MANIFEST))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val text = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            JSONObject(text).optString("version", null)
        } catch (e: Exception) {
            Log.w(TAG, "读取远程版本失败", e)
            null
        }
    }

    private fun downloadFile(urlStr: String, out: File, onProgress: (Int, Long, Long) -> Unit) {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.instanceFollowRedirects = true
        val total = conn.contentLengthLong
        conn.inputStream.use { ins ->
            BufferedInputStream(ins).use { bis ->
                FileOutputStream(out).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (true) {
                        read = bis.read(buf)
                        if (read < 0) break
                        fos.write(buf, 0, read)
                        done += read
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        onProgress(pct, done, total)
                    }
                }
            }
        }
        conn.disconnect()
    }

    private fun extractZip(zip: File, dest: File) {
        dest.mkdirs()
        val destCanon = dest.canonicalPath
        ZipInputStream(zip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                // 防 zip 目录穿越
                if (!outFile.canonicalPath.startsWith(destCanon)) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}

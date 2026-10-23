package com.jilue.game

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.jilue.game.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var server: GameServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        prepareAndLaunch()
    }

    private fun prepareAndLaunch() {
        val prefs = getSharedPreferences(Constants.PREFS, MODE_PRIVATE)
        val baseUrl = prefs.getString("baseUrl", "")?.takeIf { it.isNotBlank() }
        val owner = prefs.getString("owner", Constants.DEFAULT_OWNER) ?: Constants.DEFAULT_OWNER
        val repo = prefs.getString("repo", Constants.DEFAULT_REPO) ?: Constants.DEFAULT_REPO

        // 占位符拦截：未配置资源来源时，直接引导去设置页填写
        if (baseUrl.isNullOrBlank() && (owner == Constants.DEFAULT_OWNER || owner.isBlank())) {
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text =
                "尚未配置资源来源。\n请点击右上角「设置」，填写你的 GitHub 用户名和仓库名\n（仓库需含 assets.zip 与 manifest.json 的 Release），保存后返回重试。"
            return
        }

        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = "正在准备资源…"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ok = ResourceManager.ensureResources(
                    this@MainActivity, baseUrl, owner, repo
                ) { pct, done, total ->
                    val totalStr = if (total > 0) " / ${(total / 1024 / 1024)} MB" else ""
                    val msg = "首次需下载资源包（约 1.4 GB），请保持网络畅通…\n$pct%  (${done / 1024 / 1024} MB$totalStr)"
                    runOnUiThread { binding.statusText.text = msg }
                }
                if (!ok) throw RuntimeException("资源准备未完成")
                withContext(Dispatchers.Main) { launchGame() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text =
                        "资源准备失败：${e.message}\n请检查网络，或在右上角「设置」中更换资源来源后重试。"
                }
            }
        }
    }

    private fun launchGame() {
        startServer()
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            // 移动端适配
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        binding.webView.webViewClient = WebViewClient()
        binding.webView.webChromeClient = WebChromeClient()
        binding.webView.loadUrl("http://127.0.0.1:${Constants.PORT}/")
        binding.statusText.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
    }

    private fun startServer() {
        server?.stop()
        server = GameServer(ResourceManager.webDir(this), Constants.PORT).apply { start() }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }
}

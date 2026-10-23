package com.jilue.game

/**
 * 全局配置常量。
 * OWNER / REPO 默认值会被「设置」页覆盖；发布前请改成你自己的 GitHub 仓库。
 */
object Constants {
    /** 本地 HTTP 服务端口（localhost，Android 视为安全上下文，IndexedDB 可用） */
    const val PORT = 8080

    /** 资源包所在的 GitHub 仓库拥有者（改为你自己的用户名/组织） */
    const val DEFAULT_OWNER = "zhousanshao"

    /** 资源包所在的 GitHub 仓库名（Release 里需含 assets.zip + manifest.json） */
    const val DEFAULT_REPO = "jilue-android"

    /** SharedPreferences 文件名 */
    const val PREFS = "jilue_cfg"
}

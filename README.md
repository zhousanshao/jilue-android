# 极略三国 · Android APK

基于开源网页游戏引擎 **无名杀（noname）** + **极略** 扩展包，封装为 Android APK。

核心思路：**APK 本体只装引擎 + 游戏逻辑（很小），美术 / 音频等二进制资源从 GitHub Release 按需下载并本地缓存**，从而避开“主包 2MB / 全量 20MB”的小程序限制，也避免把 1.4 GB 资源塞进安装包。

> 说明：此前探索的「微信小程序 web-view」方案已废弃，本仓库为最终方案。

---

## 架构

```
┌──────────────────────────────────────────────────────────┐
│  APK（com.jilue.game，约 60+ MB）                          │
│  ├─ Kotlin 壳：WebView + 内嵌 HTTP 服务(NanoHTTPD)         │
│  └─ assets/web/  = 引擎代码 + 游戏逻辑(html/js/json/css)    │
└───────────────┬──────────────────────────────────────────┘
                │ 首次启动：从 GitHub Release 下载 assets.zip(≈1.4GB)
                ▼
┌──────────────────────────────────────────────────────────┐
│  本机存储 /Android/data/com.jilue.game/files/jilue/web/    │
│  ├─ (APK 内的 web 逻辑)                                    │
│  └─ (解压后的 assets.zip：audio/ image/ extension/极略/...) │
│       ↑ 二者同目录，相对路径自然解析，无需改引擎            │
└──────────────────────────────────────────────────────────┘
                │ WebView 访问 http://127.0.0.1:8080/
                ▼
           游戏运行（离线可玩，资源已本地缓存）
```

**为什么零改引擎**：无名杀用 `lib.assetURL`（默认空 → 相对路径）加载所有图片/音频。
APK 内起一个本地 HTTP 服务把“web 逻辑 + 已下载资源”作为根目录托管，引擎按相对路径请求，
代码从本地返回、缺失的二进制已从 Release 下载解压到同目录，因此完全不需要修改引擎或扩展代码。

**localhost 是安全上下文**：`http://127.0.0.1` 被 Android/浏览器视为安全源，IndexedDB（引擎存档/设置用）与 ES Module 均正常工作。

---

## 目录结构

```
jilue-android/
├─ app/                         # Android 应用（Kotlin + Gradle）
│  ├─ src/main/java/com/jilue/game/
│  │  ├─ MainActivity.kt        # WebView + 生命周期 + 进度页
│  │  ├─ GameServer.kt          # NanoHTTPD 本地服务（托管 web 根）
│  │  ├─ ResourceManager.kt     # 拷贝 web / 下载解压资源包 / 版本校验
│  │  ├─ SettingsActivity.kt    # 资源来源设置 / 清除缓存
│  │  └─ Constants.kt           # 端口、默认仓库等配置
│  └─ src/main/assets/web/      # 预置并提交（APK 内的引擎+逻辑，约 62 MB）
├─ scripts/
│  ├─ build.py                  # 拆分 dist → web(进APK) + assets.zip(资源包)
│  ├─ make_proof.py             # 本地抽样验证夹具
│  └─ proto_server.py           # 桌面端模拟 GameServer
├─ .github/workflows/build.yml  # 自动构建 + 发布 Release
└─ README.md
```

> 游戏源码（无名杀核心 + 极略扩展）可单独放在你自己的仓库（方便后续更新武将/技能）。
> 本仓库已**预置一份构建好的引擎代码到 `app/src/main/assets/web/`**，因此 CI 无需重建引擎即可出包；
> 当你更新引擎或极略后，重跑 `scripts/build.py` 刷新该目录再提交即可。

---

## 资源体积（实测）

| 内容 | 体积 | 去处 |
|---|---|---|
| `assets/web`（引擎+逻辑，进 APK） | 1244 文件 / **~62 MB** | APK 内 |
| `assets.zip`（美术+音频，Release） | 16301 文件 / **~1.39 GB** | GitHub Release，首次下载缓存 |

> 62 MB 主要是武将数据 JSON / JS，仍可进一步裁剪（关闭不需要的核心卡包）。
> 1.39 GB 几乎全是无名杀核心自带资源（约 1.18 GB）+ 极略（约 0.19 GB）。
> 若只想保留“标风 + 极略 SK”，可在引擎里精简核心卡包，资源包可降到几百 MB。

---

## 本地构建（可选，需 Android SDK + Gradle）

APK 本体很小，**推荐直接用下面的 CI 出包**，不必本地装 SDK。若想本地构建：

```bash
# 仅当更新了引擎/极略时，才需要重跑拆分（日常出包可跳过，web 已预置并提交）
python3 scripts/build.py --dist noname-engine/dist \
    --web-out app/src/main/assets/web --pack-out build/pack --version 1.0.0

# 安装 Gradle 8.9 并构建（本工程未携带 gradlew 包装器）
gradle :app:assembleDebug            # 可直接侧载安装（debug 自动签名）
gradle :app:assembleRelease          # 需在 app/build.gradle.kts 配置签名
```

> 注意：`Constants.kt` 里的 `DEFAULT_OWNER` 默认是占位符 `YOUR_GITHUB_USER`，
> 本地/出包前请改成**你的 GitHub 用户名**，或在 App 内「设置」里填写，否则 App 找不到资源包。

---

## CI 自动构建 & 发布（推荐路径）

推送 tag（如 `v1.0.0`）即触发 `.github/workflows/build.yml`：
1. 安装 JDK 17 + Android SDK（API 34）；
2. 用 Gradle 8.9 构建 `app-debug.apk`（无需签名密钥，可直接侧载）；
3. 创建 GitHub Release，附带：`app-debug.apk` 与仓库根目录的 `manifest.json`。

**资源包（assets.zip）需你手动补传**：CI 不重建引擎，因此不会生成 assets.zip。
请在 Release 页面把本地已生成的 `build/pack/assets.zip`（约 1.4 GB）加入该 Release，
App 首次启动即能从「本仓库最新 Release」自动下载它并解压缓存。

> 大文件上传建议用 `gh` CLI（网页端对超大文件可能受限）：
> ```bash
> # 安装并登录 gh 后，把资源包追加到对应 tag 的 Release
> gh release upload v1.0.0 build/pack/assets.zip --clobber
> ```
> ⚠️ 顺序：先确保 Release 里已有 `assets.zip` + `manifest.json`，再分发/安装 `app-debug.apk`，
> 否则 App 首次启动会因找不到资源包而报错（可到「设置」修改来源后重试）。

### 资源包怎么来（一次性）

`assets.zip` 已在本地 `build/pack/assets.zip` 生成好（首次由 `scripts/build.py` 产出）。
后续你更新武将/资源后，重跑 `build.py` 重新生成再上传新版本即可。

---

## 别人如何下载 & 游玩

1. 进本仓库 **Releases** 页 → 下载 `app-debug.apk` 安装（Android 允许「未知来源」）。
2. 首次打开：App 自动从「本仓库最新 Release」下载 `assets.zip`（约 1.4 GB，**请连 Wi-Fi**），
   解压到本机 `Android/data/com.jilue.game/files/jilue/web/`，之后离线可玩。
3. 若资源下载失败/想换源（如自建 CDN）→ 打开 App 右上角「设置」填自定义地址或仓库名。

> 注意：GitHub Release 单文件上限 2 GB、公开仓库带宽有限。
> 若访问量大，建议把 `assets.zip` 放到自有对象存储 / CDN，并在设置里填自定义地址。

---

## 已验证

- `scripts/build.py` 对真实 `dist`（1.5 GB）拆分成功：web 62 MB / pack 1.39 GB。
- `make_proof.py` + `proto_server.py` 端到端验证：以真实抽样资源起本地 HTTP 服务，
  `index.html`、`noname.js`、极略音频（中文路径）、极略立绘 jpg、核心音频/图片
  **全部以相对路径从 localhost 返回 200**，证明方案可行、无需改引擎。

---

## ⚠️ 版权提示

三国杀 IP 归属游卡桌游；无名杀为玩家同人开源实现（GPL-3.0），极略为其扩展包。
公开发布存在下架 / 侵权风险，**建议个人私用或社群内部分发**；正式对外发布请评估合规或改用原创三国主题外壳。

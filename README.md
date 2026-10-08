# SMRT English (Android)

`E:\ios\SMRT English`（iOS / SwiftUI）迁移而来的 Android 版本，技术栈 **Kotlin + XML**，
工程结构与约定参考 `D:\Work\Android\Projects\merchants_android`。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug
# 产物：app/build/outputs/apk/debug/smrt_english_debug_v1.0.apk
```

构建基线：AGP 8.6.0 / Gradle 8.7 / Kotlin 1.9.0 / compileSdk 34 / minSdk 26 / JVM 1.8。
`local.properties` 中的 `sdk.dir` 指向本机 Android SDK（已在 `.gitignore` 中）。

## 与 iOS 工程的对应关系

| iOS | Android |
|---|---|
| `App/SMRTEnglishApp.swift` + `RootView.swift` | `ui/splash/SplashActivity`（会话校验）+ `ui/main/MainActivity`（三 Tab） |
| `App/Theme.swift` | `res/values/colors.xml`、`themes.xml`、`UiUtils`（级别配色） |
| `App/Keychain.swift` | `base/manager/SessionManager` + `util/MMKVUtils` |
| `Networking/SMRTClient.swift` | `http/Request.kt`（OkHttp + 持久化 CookieJar）+ `http/SmrtApi.kt` |
| `Networking/SMRTHTMLParser.swift` | `parser/SMRTHTMLParser.kt` + `parser/HtmlUtil.kt`（正则逐条等价移植） |
| `Models/Models.swift` | `bean/Cafe.kt`、`bean/Library.kt`、`bean/Classes.kt`（Parcelable） |
| `Views/**` | `ui/{cafe,library,classes,login,web}/**` + `res/layout/*` |
| `KFImage`（Kingfisher） | Coil（`util/ImageExt.kt`） |
| `AVPlayer` + AV1 兜底 WebView | ExoPlayer（media3，原生支持 AV1，无兜底分支） |
| `WKWebView` + 注入 CSS | `util/WebViewHelper.kt`（同一套 CSS 与 Cookie 同步逻辑） |
| `NotificationCenter` 会话通知 | EventBus（`util/EventBusUtil.kt` + `SessionEvent`） |

## 目录约定

```
base/        BaseApplication / BaseActivity / BaseFragment / BaseViewModel / SessionManager
http/        Request（抓页面）、SmrtApi（业务接口）、PersistentCookieJar、Response、SmrtException
parser/      SMRTHTMLParser（官网 SSR 页面解析）、HtmlUtil
bean/        页面数据模型（@Parcelize）
constant/    UrlConstant（页面路径）、MConstant（存储 key）、ValueConstant
ui/          按业务模块组织：cafe / library / classes / login / splash / main / web
util/        MMKVUtils、WebViewHelper、UiUtils、EventBusUtil、ImageExt、UnPeekLiveData
view/        TitleBar、recyclerview/adapter/BindingBaseAdapter
```

页面统一走 `BaseActivity<VB, VM>` / `BaseFragment<VB, VM>` → 反射初始化 ViewBinding 与 ViewModel →
`ViewModel.load() → viewModelScope → SmrtApi` 的链路；loading、会话失效、错误提示由基类统一处理。

## 关键实现说明

- **会话保持**：`PHPSESSID` 是会话 Cookie，OkHttp 不会落盘，`PersistentCookieJar` 手动序列化到 MMKV；
  会话失效判定沿用 iOS 双重校验（302 后最终 URL 路径 + 页面是否含 `Create Account`）。
- **WebView 登录态**：`CookieManager` 与 OkHttp 是两套存储，进入课时页前需 `WebViewHelper.syncCookies()`；
  同时注入 CSS 隐藏官网 header 并修复课时页栅格（与 iOS `baseHideCSS` 完全一致）。
- **Cafe 分页**：官网 `?page=N`，末页时 Next 带 `class="disabled"`；滚动到底自动加载并去重。
- **Cafe 搜索/分类筛选的复位规则**（相对 iOS 的一处有意改进）：搜索框文本只在「搜索 / 分类」
  动态 Tab 下有意义，因此点搜索框的 ✕、或点动态 Tab 上的 ✕、或切到任何分级 Tab 时都会清空搜索框，
  并保证「Tab 高亮 = 输入框内容 = 列表筛选条件」三者一致。iOS 的实现里 ✕ 只清空文本、不切 Tab，
  且动态 Tab 的 ✕ 是装饰性图标（点了无反应），清空后会出现输入框为空但列表仍被过滤、右侧「搜索」按钮失效的状态。
- **登录页分流**：`SplashActivity` 判定首启入口，`MainActivity` 因会话失效跳转的登录页登录成功后直接返回。

## 屏幕适配（对齐 iOS 横屏与 iPad）

iOS 侧为 iPhone 竖屏 + 左右横屏、iPad 四向，并按 `horizontalSizeClass` 切换列数与内容宽度。
Android 侧对应实现：

- **方向**：不锁 `screenOrientation`，普通页面随系统旋转；`WebViewActivity` / `LessonDetailActivity` /
  `VideoPlayerActivity` 声明 `configChanges`，避免旋转时重载网页或重启播放。
  视频播放器全屏按钮会切横屏并隐藏系统栏。
- **列数**：`UiUtils.isRegularWidth()` 以最短边（`smallestScreenWidthDp >= 600`）判定紧凑/大屏，
  等价于 iOS 的 compact/regular —— 手机横屏仍是紧凑屏（2 列），与 iOS 行为一致。
  Cafe 紧凑 2 列 / 大屏 4 列，课程列表紧凑 2 列 / 大屏 3 列，横滑卡尺寸同步放大。
- **限宽居中**：课程详情 / 播放页 760dp、登录与忘记密码 480dp（对齐 iOS `centeredContent`）。
- **成员网格**：列数 = `floor((可用宽度 + 10) / 74)`，最少 4 列，对齐 iOS `GridItem(.adaptive(minimum: 64), spacing: 10)`。

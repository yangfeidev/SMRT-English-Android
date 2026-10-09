# 班级详情页子 Tab 与 iOS 一致性排查（梳理稿）

> 范围：My Classes → 单个班级卡片 → 班级详情页（`ClassCourseActivity` ↔ iOS `ClassCourseView`）
> 子 Tab：Course / Class List / Attendance / Assignments / Assessments / Gradebook(教师) / Class Info
> 状态：**P0–P6 已全部实施，并在模拟器 + 真机 PJF110 上复验通过**（17 条单测全绿）。
> 根因（含两个真机专属叠加 bug）均以活体 DOM / logcat / A/B 实验证实，详见第 1 节与 5.2。
> 实测环境：模拟器 SM-G998B（720dp）/ 真机 PJF110（360dp）/ 教师号 89371080@qq.com / 班级 25057

## 0.1 已确认的决策

| 项 | 决定 |
|---|---|
| 账号角色 | **教师号**（症状与教师端宽表格页吻合） |
| P1 缩放判据 | 改成「手机一律 0.9」，完全对齐 iOS（**本轮未做，待 P0/P2 验证后实施**） |
| 本轮范围 | **只修 P0 + P2**，真机验证通过再修其余 |
| 旋转悬浮钮 84dp 偏移 | **保留**（避让官网自带悬浮钮，避免误触） |


---

## 0. 结论速览

| 级别 | 问题 | 影响 Tab | 现象 |
|---|---|---|---|
| **P0** | 官网 `html,body{overflow-x:hidden}` 把 WebView 横向滚动范围压成 0，宽视口页**完全无法左右滑动** | 教师 Assignments、教师 Attendance | iOS 可左右滑动看全 Start/Due/Actions 列，安卓划不动（触摸无反应） |
| P1 | 0.9 缩放判定与 iOS 语义不一致（iPad 判定 vs `≥600dp` 判定） | 教师 Assignments、教师 Attendance | 720dp 手机被当平板 → 跳过 0.9 缩放，1:1 渲染，字号/可见列数与 iOS 不同 |
| P2 | 调试遗留：轴锁被整体关闭 + 调试开关/日志硬编码 | 教师 Assignments、教师 Attendance | 与 iOS「始终开轴锁」不一致；debug 包日志刷屏 |
| P3 | 学生 Assignments 行 chevron 显示条件不一致 | 学生 Assignments | iOS 恒显 chevron，安卓仅 `lessonPath != null` 时显示 |
| P4 | 学生 Class List 列间距 / 空态文案与 iOS 不一致 | 学生 Class List | 两列卡片之间无 12pt 间距（iOS 有）；iOS 无 "No Members" 空文案 |
| P5 | Class Info 空态文案 iOS 没有 | Class Info | 安卓多出 "No Class Info" |
| P6 | WebView 子 Tab 的「重试」按钮点了没反应 | 教师 4 个 WebView 页 | `btnRetry → selectTab(force=true)` 不会重载 WebView |
| P7 | viewport / CSS 注入时机弱于 iOS（非 documentStart） | 所有 WebView 页 | 首屏可能有一瞬手机版布局（实测最终会收敛，属健壮性问题） |

---

## 1. P0 根因：宽视口页横向滑动失效（真机实测）

### 1.1 现象与预期

- **iOS**：教师 Assignments/Attendance 走官网桌面断点，布局视口 992、`initial-scale/minimum-scale=0.9`；
  WKWebView 的 `scrollView` 按**布局视口宽度**持有内容，屏宽不够时可一指左右平移 → 用户描述为「左右滑动、撑满」。
- **Android**：同样注入 `width=992`，但触摸横向拖动**毫无反应**。

### 1.2 实测证据链

1. **活体 DOM 状态**（Chrome DevTools，`/sunshine/class/25057/assignments/`）：
   ```json
   { "viewportMeta": "width=992, initial-scale=1.0, minimum-scale=1.0",
     "innerWidth": 720, "docScrollWidth": 992, "docClientWidth": 992 }
   ```
   → 视口脚本**执行成功**（992 已生效），但 `scrollWidth - clientWidth = 0`：**没有横向滚动范围**。

2. **触摸事件确实进了 WebView**（`AxisLockWebView` 日志）：
   ```
   onTouch 2 (721,515) → onTouch 2 (114,515)   // ACTION_MOVE 一路收到
   ```
   但同一时间 **`scrollTo` 一次都没被调用** → 不是触摸被父容器拦截，而是**滚动容器没得滚**。

3. **官网 CSS 是元凶**：
   ```css
   html { position: relative; min-height: 100%; overflow-x: hidden; }
   body { ...; overflow-x: hidden; }
   ```
   `overflow-x:hidden` 会截断横向溢出，`documentElement.clientWidth` 直接等于布局视口（992），
   于是 `scrollWidth == clientWidth == 992`，横向滚动范围恒为 0。

4. **A/B 对照实验（决定性）**：运行时注入
   ```css
   html { overflow-x: auto !important; }   /* 或 visible */
   ```
   | 场景 | 横向滑动后 `window.scrollX` | 纵向滑动后 `window.scrollY` |
   |---|---|---|
   | 不注入（现状） | **0**（划不动） | 437 ✅ |
   | 注入 `overflow-x:auto` 后 | **272** ✅ | 437 ✅ |

   截图为证：注入后横滑可看到右侧 `Start Date / Due Date / Actions`（含删除、加人、外链、开关）。

5. **最小化验证**：
   - `html{overflow-x:auto}` 生效；**只改 `body` 无效**（必须作用于 `html`）；
   - 在 `initial-scale=0.9`（真实手机档位）下同样生效（`scrollX: 192 = 992×0.9 − 800`）；
   - 配合 `user-scalable=no` 也不影响（说明与缩放能力无关，纯粹是 overflow 的问题）；
   - 横向范围恢复后**纵向滚动不受影响**（437 仍正常）。

### 1.3 为什么 iOS 不受影响

WKWebView 的 `scrollView` 内容尺寸由**布局视口**驱动，视觉视口在布局视口内平移（`visualViewport.pageLeft` 变化），
**不依赖文档是否产生 `scrollWidth > clientWidth`**；而 Android WebView 的平移本质是**文档滚动容器的水平滚动**，
一旦 `html` 上 `overflow-x:hidden`，滚动范围归零 → 无论视口设多宽都划不动。

### 1.4 修复方案（建议）

在**宽视口页专用** CSS 中追加一条（仅 `fixedViewportWidth != null` 的页面生效，避免影响其它页面）：

```kotlin
// WebViewHelper 新增常量，并入教师宽表格页的 extraCss
const val WIDE_VIEWPORT_PAN_CSS = "html{overflow-x:auto !important;}"
```

- 作用点：`ClassCourseActivity.webViewConfigFor()` 中 `ASSIGNMENTS` / `ATTENDANCE`（教师）两支；
- 与 iOS 对齐后的预期：竖屏可左右平移看全列，横屏（悬浮钮切）一次看全；
- **风险**：`overflow-x:auto` 只影响 `html` 的横向裁剪，纵向不变（已实测）；需回归教师 Attendance 打卡卡片流。

---

## 2. P1：0.9 缩放判定与 iOS 语义不一致

| | iOS | Android |
|---|---|---|
| 判定 | `UIDevice.current.userInterfaceIdiom == .pad`（**iPhone 一律 0.9**）<br>`WebViewRepresentable.swift:529` | `smallestScreenWidthDp >= 600`（**≥600dp 即跳过 0.9**）<br>`WebViewHelper.kt:456` |

实测设备 720dp → Android 判为「平板」→ 跳过缩放，活体视口为 `initial-scale=1.0`。
而 iOS 同尺寸 iPhone 是 `initial-scale=0.9` → **两边字号、可见列数、横向可滚动距离都不同**（这正是「和 iOS 不一致」的一部分）。

> 建议：统一为「手机档位一律 0.9」。可选实现：改判据为 `screenWidthDp < 600`（Android 手机/平板分界更合理），
> 或直接与 iOS 对齐改为「非大屏一律 0.9」。需你确认取舍（涉及大屏平板观感）。

---

## 3. P2：调试遗留（必须清理）

`ClassCourseActivity.kt:741-742`
```kotlin
/** 对照实验开关：false = 禁用轴锁（排查横向滑动问题用） */
private const val DEBUG_AXIS_LOCK = false
```
- 结果：`setAxisLockEnabled(... && DEBUG_AXIS_LOCK)` 恒 false → **轴锁从未生效**；
  而 iOS 对宽视口页**始终挂轴锁**（`AxisLockScrollDelegate`，WebViewRepresentable.swift:547-557）→ 斜向拖动时 iOS 单轴、安卓双轴。
- `AxisLockWebView.kt:37` `axisLockDebug = BuildConfig.DEBUG` → debug 包每次拖动刷日志。

> 建议：P0 修好后，把轴锁打开并实测；若 `scrollTo` 覆写方案在 WebView 上仍无效（很可能，因为缩放态平移不走 `View.scrollTo`），
> 改用 `onTouchEvent` 里 `requestDisallowInterceptTouchEvent` / 直接钉 `MotionEvent` 的方案，或明确记录「Android 无法对齐轴锁」的取舍。

---

## 4. 其余 Tab 逐项对照

### 4.1 Course（原生，两角色）— 基本一致 ✅
- 单元手风琴、默认展开第一个单元、课时状态点/Complete 绿勾、点击进课时详情：**一致**。
- 差异（可忽略）：iOS 在内容区顶部渲染 28pt 课程大标题，Android 把课程名放在标题栏。
- 差异（次要）：Android 单元卡横向 padding 用 item 的 `16dp margin`，iOS 是 `padding(16)` 的间距体系，观感接近。

### 4.2 Class List（学生原生）— P4
| 项 | iOS | Android |
|---|---|---|
| 列数 | `LazyVGrid(adaptive(minimum:150))` → 屏宽自适应 | `chunked(2)` **恒 2 列**（`ClassCourseActivity.kt:391`） |
| 列间距 | `spacing: 12` ✅ | item 无横向 margin/间距 → **两张卡紧贴** ❌ |
| 空态 | 无（空数组什么都不显示） | "No Members" |

### 4.3 Class List（教师原生）— 基本一致 ✅
- Current/Removed 下划线双 tab + 人数 badge、头像/姓名/Id/Email 行：一致。
- 空态 iOS 带图标（`person.2`）+ 文案，Android 仅文案（次要）。

### 4.4 Assignments（学生原生）— P3
- 状态图标逻辑（Complete→绿勾，其余→灰钟）、标题/分组/Due、得分配色（100% 绿）：**一致** ✅
- **chevron**：iOS **恒显**（`ClassCourseTabViews.swift:178`）；Android `ivChevron.isVisible = lessonPath != null`（`ClassCourseActivity.kt:453`）❌

### 4.5 Assignments（教师 WebView）— P0/P1/P2
- 承载参数（URL `assignments/`、hideChrome、table shrink CSS、992 下限、0.9、轴锁、悬浮旋转钮）：**配置逐项对齐 iOS** ✅
- 实际失效点：P0 横滑、P1 缩放判定、P2 轴锁。
- 悬浮钮位置：Android `marginBottom=84dp`（避让官网自带 Help/主页悬浮钮），iOS 14pt —— **有意偏离**，需你确认保留。

### 4.6 Attendance
- 学生：WebView，`attendance/` + hideChrome：一致 ✅
- 教师：WebView，hideChrome + 列宽修正 CSS + table shrink + 768 下限 + 0.9 + 轴锁：**配置一致** ✅，但同受 P0（若该页出现表格）影响。
  注：实测当前班级教师 Attendance 是「打卡卡片流」，无表格 → 视口脚本正确保持手机版（与 iOS 行为一致）✅

### 4.7 Assessments（两角色 WebView）— 一致 ✅
- 路径 `evaluation/`、hideChrome、教师额外隐藏圆钮 CSS：一致 ✅

### 4.8 Gradebook（教师 WebView）— 一致 ✅
- `gradebook/` + hideChrome，无固定视口：一致 ✅

### 4.9 Class Info（原生）— P5
- label/value 键值卡、Teacher 多行值换行拼接：一致 ✅
- Android 多 "No Class Info" 空态（iOS 无）。

### 4.10 框架级（两角色）
| 项 | iOS | Android | 结论 |
|---|---|---|---|
| Tab 胶囊横向滚动 + 选中居中 | ✅ | ✅ | 一致 |
| 学生 6 tab / 教师 7 tab（Gradebook 判定） | ✅ | ✅ | 一致 |
| WebView 按 tab 各建一个、切回缓存 | ✅ | ✅ | 一致 |
| 返回键：WebView 有历史先 goBack | ✅ | ✅ | 一致 |
| 横竖屏：切不支持横屏的 tab 回竖屏 | ✅ | ✅ | 一致 |
| 顶部加载进度条 | ✅ | ✅ | 一致 |
| 教师宽表格页「重试」 | 不涉及（WebView 自管） | P6：按钮无效 | 待修 |
| viewport/CSS 注入时机 | `.atDocumentStart`（首帧前） | `onPageStarted` + `evaluateJavascript` | P7：可能首帧闪手机版 |

---

## 5. 建议实施顺序

**第 1 步（P0，必须）**：`html{overflow-x:auto !important}` 并入教师宽表格页 CSS → 真机验证横滑/纵滑/横竖屏。
**第 2 步（P2）**：清理 `DEBUG_AXIS_LOCK` 与调试日志；把轴锁打开并 A/B 实测（决定保留还是改实现）。
**第 3 步（P1）**：确认缩放判据取舍后与 iOS 对齐。
**第 4 步（P3–P6）**：零散 UI/交互补齐（chevron、列间距、空态、重试）。
**第 5 步（P7）**：可选加固注入时机（`WebViewCompat.addDocumentStartJavaScript`，需加 `androidx.webkit` 依赖）。

**风险与注意**
- 第 1 步会让「原本不需要横滑」的页面（如无表格的打卡卡片流）在极窄屏下出现可横滑——已限定只在宽视口页注入，且实测无表格时视口不切换，影响可控。
- 本仓库有**大量未提交改动**（教师端整套移植），建议先提交/备份再改，便于逐项回滚。
- 真机当前装的是 13:37 的 APK（含 `DEBUG_AXIS_LOCK=false`），改动后需重新 build+install 复验。

## 5. 实施记录

### 5.1 改动清单

| 文件 | 改动 |
|---|---|
| `util/WebViewHelper.kt` | 新增 `WIDE_VIEWPORT_PAN_CSS`；缩放判据去掉 `≥600dp` 豁免（P1）；视口脚本改为「有界重试 + 看守校正 + 空文档兜底」；`mainHandler` 改 lazy（可单测）；清理 `configWebView` 死参 |
| `ui/classes/ClassCourseActivity.kt` | 教师 Assignments/Attendance 追加横滑 CSS；轴锁恒开（P2）；删 `DEBUG_AXIS_LOCK`；学生 Class List 自适应列数+12dp 间距（P4）；Assignments chevron 恒显（P3）；去掉 iOS 没有的空态文案（P4/P5）；重试按钮对 WebView 改 `reload()` + 错误态按 tab 归属（P6） |
| `view/AxisLockWebView.kt` | 删调试日志；阈值 18px → 6dp 密度换算（P2） |
| `util/UiUtils.kt` | 新增纯函数 `adaptiveColumns`（自适应列数算法，供两处网格共用 + 单测） |
| `test/.../ClassTabParityTest.kt` | 新增 13 条回归断言（列数算法 / 横滑 CSS / 视口脚本契约 / Tab 定义） |

### 5.2 修复过程中暴露的另外两个真机专属 bug（已一并修掉）

真机（PJF110，360dp 常规手机）与模拟器（720dp）表现不同，逐层定位到三个叠加原因：

1. **`overflow-x:hidden`**（P0，主因）：横向滚动范围恒为 0 → 已修。
2. **`appendChild` 空文档崩溃**：日志实证
   `Uncaught TypeError: Cannot read properties of null (reading 'appendChild')`。
   `onPageStarted` 注入时 `document.head` / `documentElement` 仍为 `null`，
   异常让**整段视口脚本中断**，后续 load 监听与重试全部注册不上。
   模拟器慢、文档已就绪所以没暴露；真机快 → 表格页停在手机版。已加延后重试。
3. **官网覆盖 viewport**：官网 JS 在我们之后重写 `meta viewport`，最终停在
   `device-width`，表格被手机版 CSS 压到 288px。原脚本「设一次就不管」→
   改为看守定时器：窗口期内只要表格存在且当前 viewport ≠ 目标值就重设。
   另：手机版布局下 `#content` 已被抹平到 372px，量宽不可信 →
   量不到时回落到下限（992），不再以 `w > 320` 作为放弃门槛。

### 5.3 真机复验结果

**模拟器 SM-G998B（720dp，教师号）**

| 用例 | 结果 |
|---|---|
| Assignments 横滑 | ✅ `scrollX` 0 → 272（修复前恒 0） |
| Assignments 纵滑 | ✅ `scrollY` → 421 |
| 斜向拖动（轴锁） | ✅ `(272,0)` 斜拖 → `(272,151)`，横向钉住 |
| Attendance / Assessments / Gradebook | ✅ 无表格页保持 `scrollX=0`，零影响 |
| 原生 Tab（Course/Class List/Class Info） | ✅ 不受影响 |
| 冷启动 ×3 | ✅ 三次都稳定进宽表格 |

**真机 PJF110（360dp 常规手机）**

```
viewport = width=992, initial-scale=0.9, minimum-scale=0.9   ← P1 生效
overflow-x = auto                                            ← P0 生效
横滑 → scrollX = 330     纵滑 → scrollY = 207                ← 双向都可用
```

### 5.4 单元测试

`./gradlew :app:testDebugUnitTest` → **17 tests, 0 failures**（含 13 条新增一致性断言）。
覆盖：自适应列数不变量、横滑 CSS 必须作用于 `html`、视口脚本的上下限/缩放/有界重试/
官网覆盖重设/空文档兜底、Tab 顺序与角色可见数。

### 5.5 本轮未做

- **P7**：`documentStart` 注入加固（需加 `androidx.webkit` 依赖）。当前用「有界重试 + 看守校正」已能达到同等效果，可暂缓。

### 5.6 注意事项

- 本仓库仍有**大量未提交改动**（教师端整套移植 + 本轮改动），建议尽快提交存档。
- 旋转钮 `bottomMargin=84dp` 按你的决定**保留**。
- 若后续官网把表格从 `.rightside table` 改结构，视口脚本的表格探测需同步更新（脚本与 CSS 都以此选择器为准）。



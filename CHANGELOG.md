# 单词地下城 · 迭代日志（CHANGELOG）

> 项目：单词地下城（Word Dungeon）｜定位：应试学生向背单词战斗 Roguelike（完全离线）
> 版本规则：**小改动 +0.1，大改动 +1**（用户锁定，见偏好）

## V10.0.3 · 模拟器定位并修复原生 UI 崩溃三连（2026-10-26）
- **背景**：V10.0 全原生重构（零 WebView/零网络，UI 全 Android 原生 View，引擎内存直调）后，V10.0/10.0.1/10.0.2 真机（一加13）均"闪退"。沙箱无 KVM，用 Android 14 x86_64 模拟器（-no-accel 软件模拟）复现并定位到**确定的代码根因**。
- **根因 1（核心）：`Ui.spacer` 未设置 LayoutParams**。旧实现只 `setMinimumHeight`，空 View 被 addView 到 LinearLayout 时拿到默认 LayoutParams，测量异常（一个 spacer 撑满剩余高度，bounds 356→2221），把主页后续所有内容（状态/词书/难度/模式/按钮）挤掉——主页只显示标题。修复：spacer 明确设置 `LinearLayout.LayoutParams(px, px)` 固定宽高（垂直容器作竖向间距、水平容器作横向间距）。
- **根因 2：`SettingsView.row()` 重复 addView**。row() 内部已把 box 加入容器并返回内部 TextView，调用处又 addView 返回值 → `IllegalStateException: The specified child already has a parent`。修复：row() 返回 box（不 addView），调用处 addView(box) 并通过 getChildAt(0) 取 TextView，监听设到 box。
- **根因 3：`AchievementsView.card()` 同型重复 addView**。card() 内部 addView 后返回，调用处又 addView。修复：card() 只返回 box，spacer 由调用处补。
- **模拟器全流程验证（release 真机等同代码）**：主页完整渲染、后端就绪（词库 7388 词，高考3674/四级3846/六级5406/考研4801）、战斗页四题型、答对攻击敌人掉血、答错玩家受击、连击/击杀/进入第2层/精英敌人（金色血条）、词性标注（N./ADV.）、错词归档（按错误次数+难度）、错词复习（记住/再复习）全部正常。
- 版本 10.0.3 / versionCode 16（小改动 +0.1）。

## V10.0 · 全原生重构（零 WebView/零网络）（2026-10-26）
- 用户拍板"用全原生的去写"：彻底移除 WebView/JS 桥/HTTP/Capacitor 通道，UI 全部 Android 原生 View（HomeView/BattleView/ResultView/WrongbookView/SettingsView/AchievementsView + Ui/HpBar），后端引擎全部 Java 类内存直调 BackendRouter——物理上不存在"连接"环节。
- 新增 App.java（Application，未捕获异常落盘 crash.log + takeCrash 自诊断）；MainActivity 改为 FrameLayout 容器 + Screen 枚举切换。
- V10.0.1：崩溃落盘 + 主页红字 + View 构造 try-catch；V10.0.2：onCreate 全兜底 + show 判空 + Toast。大改动 +1（10.0），修复 +0.1/+0.2。

## V9.0 · 后端通道架构重构：Capacitor 插件桥（2026-10-26）
- **根因定位**：真机"本地后端未启动"的根因是前端 HTTP fetch 本地端口整条链路在 WebView 中不可靠（页面 scheme 与 mixed content、CORS、WebView 网络限制任一环都可能阻断，无 logcat 无法精确定位）；已排除词库丢失、NanoHTTPD 未打包、cleartext 未放行、CSP、minify。
- **根治方案**：新增 `bridge/WordDungeonPlugin`（@CapacitorPlugin + call 方法），前端通过 `Capacitor.Plugins.WordDungeon.call({route,payload})` **直调 Java 后端**，彻底绕开 HTTP/端口/cleartext/CORS/mixed-content 网络层。
- **单一逻辑源**：新增 `server/BackendRouter`（纯逻辑分发，不依赖 NanoHTTPD），插件与 HTTP 服务共用；`LocalServer` 精简为委托 BackendRouter（保留浏览器调试/云端通道）。
- `MainActivity.onCreate` 注册插件桥；HTTP 服务仍启动（后台线程）。
- 前端 API 层：App 内优先插件桥（init/req 全部走 call），浏览器仍走 HTTP 配置；等待/重试逻辑保留。
- JVM 编译通过；APK 内已确认含插件桥代码（apkanalyzer 145 个 com.guzifeng 类；dexdump 对 6MB dex 误报，弃用）。版本 9.0 / versionCode 10（大改动 +1）。

## V9.0.1 · 插件桥时序修复：动态获取 + 轮询等待 + 失败原因诊断（2026-10-26）
- 真机截图证实后端启动正常（系统 Toast「本地后端已启动:8080」），但前端仍报"未启动"——原因：Capacitor 原生插件是**页面加载后注入**的，前端在页面加载时静态判断 `window.Capacitor.Plugins.WordDungeon` 会拿到 null，导致回退到被 WebView 阻断的 HTTP 探测。
- 修复：插件改为**动态获取 getCap()**；App 内 init 时**轮询等待插件注入（至多 10 秒）**；失败时记录 `capFail` 并把原因拼进页面提示（插件不可用/插件调用失败:xx/HTTP 探测失败），下次截图自带诊断信息。
## V8.0.2 · 后端本地部署确认 + 失败诊断（2026-10-26）
- 用户确认：后端必须本地部署（App 内 NanoHTTPD + SQLite），**不上云**——架构方向不变，完全离线。
- 真机后端仍连不上（V8.0.1 后 toast 显示"本地后端未启动"）：为定位根因，加固诊断能力：
  - `MainActivity.startLocalServer` 捕获 **Throwable**（含 Error），每端口失败 Logcat + **系统 Toast 直接显示失败原因与端口**；成功 Toast 显示实际端口。
  - 启动尝试 8→10 端口；`backendStatus` 静态变量记录最终状态。
  - 前端后端就绪等待 12 轮→**40 轮**（词库 653KB 解析在真机需数秒）；失败后 **15 秒自动重试**。
## V8.0.1 · 修复真机词书全 0（2026-10-26）
- **现象**：真机（一加13）安装 V8.0 后设置页词书全部 0 词、掌握进度 0/0、无法开始游戏。
- **根因**：`capacitor.config.json` 默认 `androidScheme:"https"`——页面运行在 `https://localhost`，WebView 混合内容策略拦截 fetch `http://127.0.0.1:8080`，本地 Java 后端连不上；而 V8.0 已删除前端内嵌词库，词书数据全靠后端 → 全 0、无法出题。
- **修复**：`androidScheme` 改为 `http`、`allowMixedContent` 置 true；`MainActivity` 额外 `setMixedContentMode(ALWAYS_ALLOW)` 双保险；后端启动/失败写 Logcat（Tag: WordDungeon）；前端版本号更新为 V8.0 · Java 后端版，后端未启动时明确 toast 提示。
- **验证**：APK 内 `assets/capacitor.config.json` 已确认 http+allowMixedContent；index.html 已同步；release+debug 构建成功。
## V8.0 · 原生 Java 后端架构重构（2026-10-26）
- **核心诉求**：用户要求"不要把代码全部用 HTML 写，要有后端"，并拍板**用 Java**——不引入 Python 运行时，完全离线。
- **技术架构**：App 内用 **NanoHTTPD**（单依赖 `org.nanohttpd:nanohttpd:2.3.1`）在后台线程启动 `127.0.0.1` 本地 HTTP 服务；前端瘦身为纯 UI + 接口调用；词库放 `assets/data/` 由 Java 读取。
- **Java 后端 14 个类**（包 `com.guzifeng.worddungeon`）：
  - 模型：`WordEntry`、`WordRec`（含快刷字段）、`Quiz`、`GameConfig`（全部数值常量）。
  - 引擎：`WordBank`（词库加载/查询/干扰词）、`SrsEngine`（θ 自适应 + SM-2 + 题型阶梯 + 到期队列）、`QuizEngine`（闯关/无尽出题）、`QuickEngine`（快刷三档）、`DamageCalc`（速度/连击/复仇/暴击）、`EnemyModel`（normal/elite/boss + 无尽缩放 + 冠军）、`RunState`（单局错词 2 次封顶）。
  - 服务/存储：`LocalServer`（NanoHTTPD，13 条路由对齐 FastAPI + Bearer 鉴权）、`ProgressStore`（SQLite，单用户 id=1）、`MainActivity`（启动/停止服务）。
- **前端瘦身**：`word-dungeon-demo.html` 从 824KB 降到 **174KB**——删除内嵌 7388 词（单行 584KB）；`WMAP` 改为渲染缓存（`cacheEntry`）；App 内自动探测端口（8080–8087）并等待后端就绪；出题/敌人/统计全部走 API。
- **修复的真实 bug**：
  - 无尽模式普通/精英敌人在冠军层（每 5 层）未应用冠军强化且标记写死 false → 已应用 HP×1.25、ATK+3、名称前缀"冠军·"。
  - `MainActivity.onDestroy()` 访问权限弱于父类（protected vs public）导致编译失败 → 改 public。
  - 非自适应难度字段缺失时被解析为 0（无效档）→ 改为仅在字段存在且非 null 时取值。
- **验证**：纯 JVM 综合测试 **53 项全部通过**（词库加载、题型阶梯、毕业、回退、到期队列、四题型、无尽难度、快刷三档、伤害各倍率、敌人生成、冠军、单局封顶）；release + debug APK 均构建成功。
- 版本号：`versionCode 9 / versionName 8.0`、package.json 8.0.0；Python FastAPI 保留作浏览器开发/云端可选。

## V6.1 · 美式真人发音（2026-10-26）
- 进入每个新单词卡片时自动播放**美式真人发音**；每题只发音一次，不重复。
- **联网**→在线发音（有道词典 dictvoice，`type=2` 美音，URL 直出 MP3，无需 key、覆盖全）；**离线**→不发音、不发请求，自动适配，不影响离线游玩。
- 全程异步、**非阻塞**：音频在后台加载播放，不阻塞渲染与交互，用户无卡顿感知；播放失败静默降级。
- 设置页「声音与触感」新增「单词发音」开关，默认开启并持久化。

## V7.0 · 快速刷题模式（2026-10-26）
- **新增快速刷题模式**：主页第三 tab「快刷」，分**普通 / 困难 / 地狱**三档，连续战斗、击杀后立刻下一个、不抽卡。
- **词频 × 错误驱动动态出题**：
  - 词频排名转高频度分 `freqScore=max(0,1−log10(f)/log10(10000))`；普通档 freqPow=1.0（高频优先）、地狱 0.2（低频难词也出）。
  - 错误分级矩阵 `wrong_by`：记录「在哪个档 × 哪个题型错过」；错误加权 `errorBoost=Σ wrong_by×档位系数(1/1.5/2)×题型系数(1/1/1.3/1.6)`，错误跟随到困难/地狱、反复出现。
  - 高频词一次答对被「刷掉」→ 困难/地狱抑制到 5%~8% + 3% 轮播；答错 → 权重显著抬高。
- **地狱拼写为主、拼写压轴**：spellBias=0.75 从已走完识别阶梯（step≥3、词长≤8）的词抽拼写；新词先识别、绝不拼写；拼写限时地狱 +3s。
- 错词本新增「在哪里错过」标签（如 普通判断×1 / 地狱拼写×2）。
- 后端 `core.draw_quick` + `/quiz/draw`、`/quiz/answer` 支持 mode=quick；前端 API 优先 + 本地等价函数（drawQuickLocal）离线兜底。
- 文献依据：Coxhead(2000)、Dang & Webb(2016) 高频词；Edge(2012)、Settles & Meeder(2016) 自适应/间隔重复。

## V6.0 · 完全离线版（2026-10-26）
- **产品形态**：完全离线。默认不发起任何网络请求；全部 7388 词（高考/四级/六级/考研）内置本地打包。
- 设置页新增「云端后端（可选）」：留空=纯离线；填入自建 FastAPI 地址可启用多设备进度同步（可选增强，不影响离线游玩）。
- API 层：未配置后端地址时 `init()` 直接返回离线，不联网。
- 关于页/页头版本号更新为 V6.0 · 完全离线版。
- 归档：新增本日志 + `docs/troubleshooting-archive.md`（问题档案）。

## V5.2 · 卡死修复（2026-10-26）
- **问题**：API 模式下后端断连，`/quiz/answer` 请求挂起无超时 → `locked` 永久卡死，界面无法点击。
- **修复**：全部 API 请求 8s 超时（登录握手 5s）；超时自动解锁并降级为本地结算；弹出「网络异常 · 已切换离线模式」提示。
- **验证**：playwright 拦截 answer 挂起，8s 后解锁、本地结算、可继续连续作答。

## V5.1 · 返回键修复（2026-10-26）
- **问题**：系统返回键默认退出 App，且首次修复因未安装 `@capacitor/app` 插件而失效。
- **修复**：安装 `@capacitor/app@6.0.3` 并 `cap sync`（AppPlugin 编入 classes2.dex 已验证）；返回键改为按页面层级返回上一级，战斗中先弹「放弃本层战斗？」确认框，主页再按才退出。
- **验证**：浏览器模拟三场景（设置→主页 / 战斗→确认框→再按离开 / 主页退出）。

## V5.0 · 前后端接入（2026-10-25 前后）
- 前端新增 API 层（init/req/draw/answer/due/wrongbook/wrongReview/stats/bankMeta）；`API.base` 可被 `localStorage.wd_api_base` 覆盖；后端加 CORS。
- 后端 FastAPI 完整实现：θ 自适应、SM-2、题型阶梯、错词分级、单局封顶、无尽、复习队列、统计。
- 离线降级保留：后端不可达时自动回退内嵌词库本地逻辑。

## V4.8 · 音频与设置（V4.8 系列，含 V4.7.x）
- 程序化合成音效（Web Audio，零音频文件）：命中/暴击/复仇/受击/升级/心流；BGM 音序器随场景切换；声音开关 + 音量滑条；震动反馈。

## V4.7 · UI 改版（手绘线框主页）
- 主页：横幅 + 发射卡（模式 tab / 设置行 / 开始按钮）+ 底部三入口；设置页收纳次级内容；词书/难度迁入设置；成就/设置并列；一加13（412dp）撑满全屏。
- 修复释义框抖动：删除整屏震动，震动只作用于敌人卡；模式 tab 去掉两个 ⓘ 解释（用户：这两个解释不要）。
- ⓘ 角标点击弹小窗（无需在页面铺解释文本）。

## V4.5 期 · 图标
- 西式奇幻版图标 + 像素风图标（16-bit，已集成 Android）。

## V4.4 · 拼写输入修复（用户多点反馈）
- 输入自动反字 → 权威输入序列 `seq`（keydown 维护，value 只回显，防输入法乱序/联想/自动纠错）。
- 手机自带删除键无效 → keydown 显式处理 Backspace。
- 拼写题回车才确认；计时结束自动按内容提交；限时=基础 4s + 每字母 2s（含反应余量）。
- 拼写答错展示正确答案并停留 1 秒。

## V4.3 · 题型阶梯重构
- step0 判断 → step1 英选中 → step2 中选英 → step3 拼写（压轴）→ 毕业；超长词（>8 字母）不拼写。
- 前端离线逻辑与后端 core.py 同步实现（θ/SM-2/阶梯/封顶）。

## V4.2 期 · 错词与复习
- 错词归档按错误次数分级（1=生疏/2=易错/≥3=顽固）；错词本与泉水复习按「顽固→易错→生疏」优先；答对降级。
- 单局错词 2 次封顶（run_state wrong/seen）；新词每日上限 20。

## V4.1 期 · 核心循环确认（用户多次拍板）
- 确认玩法「答对=攻击、答错=受击」；词性标注（同拼写不同词性需区分）；成瘾/心流机制（连击/凝神/每日目标/成就/等级）；打击/升级/复仇动效；无尽模式（normal/elite/spring/shop/boss 节点链，每 5 层冠军 Boss）。
- 词库：开源 ECDICT，7388 词，释义取常用义、相近释义做干扰项。

## 交付口径（长期规则）
- **双交付**：飞书 GDD（线上文档）+ 可玩 Demo/APK；最新版本以 present_files 交付。
- 绝大多数操作如后端，前端只负责 UI 接口（用户偏好）。
- 保留现有色系 `:root`（深蓝地下城 #0d1828 系）。

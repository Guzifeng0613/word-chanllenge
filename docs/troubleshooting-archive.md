# 单词地下城 · 问题档案（Troubleshooting Archive）

> 用途：记录开发/测试/打包过程中遇到的真实问题与解法，**遇到相似问题时先来这里查**。
> 每条档案：现象 → 根因 → 解法 → 验证 → 适用版本。
> 检索提示：可用关键词查找，如「卡死」「返回键」「抖动」「拼写」「APK」「keystore」「playwright」「git」「画布」。

---

## 1. 游戏突然卡住、所有点击无效（"一开始好好的，突然没法用"）
- **现象**：API 模式下玩着玩着突然无法点击选项，界面正常、倒计时也像停了。
- **根因**：后端断连/网络不稳时，`/quiz/answer` 等 fetch **无超时**，长时间 pending；战斗锁 `b.locked=true` 在 `resolve()` 开头置位后永远不释放，`answer()` 的入口守卫 `if(b.locked) return` 吞掉全部点击。
- **解法**：
  1. `API.req()` 用 `Promise.race` 加 **8s 超时**；`API.init()` 加 **5s 超时**。
  2. 超时 → `catch` → `API.on=false; b.locked=false;` 再走本地结算（自动降级离线）。
  3. 降级时 `apiToast('网络异常 · 已切换离线模式')` 提示用户。
- **验证**：playwright 拦截 answer 请求永不响应；8s 后 locked 释放、answered+1、下一题正常出、可继续连续作答；后端在线时 init/出题不受影响。
- **适用版本**：V5.2+。
- **关键词**：卡死、无法点击、locked、超时、降级、api-timeout。

## 2. 拼写输入自动反字 / 手机删除键无效
- **现象**：拼写题输入字母会乱序/自动纠正；手机自带删除键删不掉。
- **根因**：依赖 `<input>` 的 input 事件，部分虚拟键盘/输入法不派发 keydown、或触发联想纠错重排。
- **解法**：引入**权威输入序列 `seq`**——由 `keydown` 事件维护（Enter 确认、Backspace 显式 pop、单字符 push、非字母拦截），`si.value` 只做回显；`input` 事件做兜底（退格/增量合并/子序列校验，重排则强制恢复回显）。
- **验证**：真机虚拟键盘测试；限时结束按 `seq` 自动结算。
- **适用版本**：V4.4+。
- **关键词**：拼写、反字、删除键、输入法、seq。

## 3. 释义框 / 题目框抖动
- **现象**：答题时整个界面抖动，释义提示框抖动一会才正常。
- **根因**：受击/攻击动效对整屏做了震动（`body`/全屏 shake），命中震动波及阅读区。
- **解法**：删除整屏震动；震动只作用于敌人卡（`enemycard` 加 `enemyhit/enemycrit` 类 + `offsetWidth` 重触发），题目/释义框保持稳定。
- **适用版本**：V4.6+。
- **关键词**：抖动、震动、敌人卡、释义框。

## 4. 系统返回键退出页面而不是返回上一级
- **现象**：真机按系统返回键直接退出 App（或首次修复后仍无效）。
- **根因**：① 未安装 `@capacitor/app` 插件，`window.Capacitor.Plugins.App` 不存在，backButton 监听从未生效；② Capacitor 默认返回键行为是退出。
- **解法**：
  1. `npm i @capacitor/app` → `npx cap sync android`（确认 `android/app/src/main/assets/capacitor.plugins.json` 含 `com.capacitorjs.plugins.app.AppPlugin`）。
  2. 前端 `App.addListener('backButton', handleAndroidBack)`：设置/错词本/成就/统计/结算 → 主页；战斗 → 弹「放弃本层战斗？」确认框（再按一次才离开）；泉水/商店/节点选择 → 回战斗；主页 → exitApp。
- **验证**：浏览器模拟三场景；解包 APK 确认 AppPlugin 在 classes2.dex；安装新 APK 真机测试。
- **适用版本**：V5.1+。
- **关键词**：返回键、backButton、App 插件、Capacitor、退出。

## 5. 模板/外部进程回滚 HTML CSS 修改
- **现象**：改完的 CSS/HTML 又变回旧版。
- **根因**：某些模板/脚手架进程会覆盖 HTML 文件改动。
- **解法**：用 python 脚本强制写入（`open(path,'w')` + 断言 old 存在），再用 `grep`/`python` 校验关键串存在。
- **适用版本**：全程。
- **关键词**：回滚、覆盖、HTML、CSS。

## 6. Playwright 无法启动浏览器
- **现象**：`chromium.launch()` 报浏览器未找到。
- **根因**：PLAYWRIGHT_BROWSERS_PATH 未设置；chromium 版本符号链接缺失（1234/1228 需指向 1217）。
- **解法**：`export PLAYWRIGHT_BROWSERS_PATH=$HOME/.cache/ms-playwright`；`ls $HOME/.cache/ms-playwright` 检查并建立符号链接指向可用版本。
- **适用版本**：测试环节。
- **关键词**：playwright、chromium、浏览器、launch。

## 7. GitHub 推送失败（HTTPS token 无效）
- **现象**：`git push` 用 GITHUB_TOKEN（HTTPS）认证失败。
- **根因**：该仓库 HTTPS token 通道无效。
- **解法**：改用 SSH key（`git@github.com:Guzifeng0613/word-chanllenge.git`）；`git config user.name Guzifeng0613`。
- **适用版本**：提交环节。
- **关键词**：git、push、GitHub、token、SSH。

## 8. git 仓库间歇性报 "not a git repository"
- **现象**：`git status`/`git add` 偶发失败，`.git/HEAD` 消失。
- **根因**：仓库被并发进程（多 agent/任务）同时操作，锁冲突。
- **解法**：稍后重试；或显式 `--git-dir`；避免与其它任务并行操作同一仓库。
- **适用版本**：提交环节。
- **关键词**：git、HEAD、锁、not a git repository。

## 9. 飞书画布（html5-block）超限被拒
- **现象**：飞书文档里的 HTML 画布发布失败/截断。
- **根因**：html5-block 硬上限 **500KB**；骨架 CSS 注释里的示例 markup 会被 `_SLOT_OPEN` 误匹配。
- **解法**：画布文件压到 ≤500KB（精简 CSS/去掉大图）；CSS 注释不写结构标签字面量（改纯文字说明）；图片用 data:URI 或公网 URL。
- **适用版本**：飞书文档/画布发布。
- **关键词**：画布、html5-block、500KB、_SLOT_OPEN、飞书。

## 10. Gradle 找不到 release keystore / 签名失败
- **现象**：`assembleRelease` 报 `Keystore file '...' not found`。
- **根因**：`build.gradle` 里 `storeFile file('...')` 相对于 **app 模块目录**（`android/app/`）解析。
- **解法**：keystore 放 `.build/`（已 gitignore），路径写 `file('../../.build/worddungeon.keystore')`；签名密码不入库。
- **验证**：`keytool -printcert -jarfile app.apk` 显示 Signer；构建成功。
- **适用版本**：V5.1+ 打包。
- **关键词**：keystore、签名、assembleRelease、storeFile、密码。

## 11. 修改 build.gradle/package.json 后 APK 未生效
- **现象**：改了版本号或装了插件，旧 APK 行为不变。
- **根因**：Capacitor 工程变更未同步到 android。
- **解法**：改动收敛后必须 `npx cap sync android` 再 `./gradlew assembleDebug|assembleRelease`；校验包内 `assets/public/index.html` md5 与 `www/index.html` 一致。
- **适用版本**：打包环节。
- **关键词**：cap sync、gradlew、md5、包内资源。

## 12. Playwright 拦截响应导致 CORS 失败（init 降级为离线）
- **现象**：测试里 route 拦截 `/auth/guest` 返回 200，但前端 `API.on=false`。
- **根因**：`r.fulfill()` 默认不带 CORS 头，浏览器拦截跨域响应 → fetch 失败。
- **解法**：fulfill 时带 `Access-Control-Allow-Origin: *`（含 Allow-Headers/Allow-Methods）；且 `loadApiData` 会并发请求 due/wrongbook/stats，需全部拦截，否则任一失败会把 `API.on` 置 false。
- **适用版本**：自动化测试。
- **关键词**：playwright、route、fulfill、CORS、init。

## 13. 请求挂起类问题的通用排查清单
- 所有对后端的请求都必须有超时（race/AbortController），防止锁死；
- `locked` 标志要有**全部**出口路径的复位（成功/失败/超时/重入）；
- 降级路径必须可继续游玩（本地词库结算），而不是停在错误状态；
- 真机与浏览器行为差异点：输入法（keydown vs input）、返回键（Capacitor）、网络（挂起 vs 拒绝）。

## 14. Java 重写父类方法访问权限变弱导致编译失败
- **现象**：V8.0 构建报 `onDestroy() in MainActivity cannot override onDestroy() in BridgeActivity; attempting to assign weaker access privileges; was public`。
- **根因**：`BridgeActivity`（Capacitor）的 `onDestroy()` 是 **public**，子类用 `protected` 覆盖等于收窄访问权限，Java 不允许。
- **解法**：覆盖方法的访问修饰符不得严于父类——改为 `public void onDestroy()`。
- **验证**：重新 assembleDebug 编译通过。
- **适用版本**：V8.0+。
- **关键词**：override、weaker access、onDestroy、public、protected、BridgeActivity。

## 15. 无尽模式冠军（Champion）敌人未强化 / 标记丢失
- **现象**：JVM 测试中第 5 层（应为冠军层）普通敌人 `champion` 标记为 false，且数值未强化。
- **根因**：`EnemyModel.spawn` 无尽分支里，普通/精英敌人路径把 `envelope` 的 champion 参数写死 `false`，也没套用冠军 HP/ATK 加成（只有 boss 分支处理了）。
- **解法**：普通/精英路径同样读取 `champion=isChampionDepth(depth)`，套用 `HP×ENDLESS_CHAMPION_HP(1.25)`、`ATK+ENDLESS_CHAMPION_ATK(3)`，名称加前缀"冠军·"，并把 champion 透传给 envelope。
- **验证**：JVM 测试第 5 层 `champion=true` 且数值强化；53 项全过。
- **适用版本**：V8.0+。
- **关键词**：无尽、endless、冠军、champion、EnemyModel、强化。

## 16. org.json 缺失字段被 optInt 误解析为 0
- **现象**：出题请求未带 `difficulty` 字段时，后端可能拿到 `difficulty=0`（无效难度档），导致按档抽词落空。
- **根因**：`JSONObject.isNull(key)` 对**不存在的 key** 返回 false（它只判断值是否为 NULL），`optInt(key)` 对缺失 key 返回默认 0，于是 `isNull?null:optInt` 误得 0。
- **解法**：先判存在再判空：`b.has(key) && !b.isNull(key) ? b.optInt(key) : null`。字符串字段用带默认值的 `optString(key, null)` 更安全。
- **验证**：自适应模式（不传 difficulty）走 θ 分支不受影响；非自适应传 1/2/3 正确。
- **适用版本**：V8.0+。
- **关键词**：org.json、isNull、optInt、缺失字段、has、difficulty。

## 17. 纯 JVM 验证 Android 工程核心逻辑的方法
- 后端核心类（引擎/模型）不依赖 Android 运行时，只有 `WordBank` 引用 `Context/AssetManager`。
- 解法：给 `WordBank` 加 `loadForTest(dir)`（从文件系统读词库）与 `parse(json,json)`；编译时用 SDK 的 `android.jar` 提供 stub 类（仅编译，不执行），运行时 classpath 加真实 `org.json` jar（Maven `org.json:json`）。
- 依赖 Android 的 `LocalServer`/`ProgressStore`（SQLite、HTTP 层）需 Robolectric 或真机/模拟器验证，不能纯 JVM。
- **适用版本**：V8.0+ 自动化验证。
- **关键词**：JVM 测试、android.jar、stub、loadForTest、org.json、Robolectric。

## 18. 真机"本地后端未启动"——HTTP fetch 本地端口链路不可靠（V9.0 根治）
- **现象**：V8.0/V8.0.1/V8.0.2 真机（一加13）前端始终提示"本地后端未启动·词书不可用"，系统 Toast 未能捕获有效信息。
- **已排除**：assets/data 词库完好（653,704 B）、NanoHTTPD 已入 dex（640 处引用）、INTERNET 权限、networkSecurityConfig 放行 127.0.0.1/localhost、index.html 无 CSP、minifyEnabled=false、androidScheme=http + allowMixedContent=true 均在 APK 内确认。
- **未证实的可能**：WebView 页面 scheme（http vs https）与 mixed content 拦截、WebView 对 127.0.0.1 fetch 的网络限制、CORS/预检。
- **根治**：不再依赖 HTTP。新增 Capacitor 原生插件桥 WordDungeonPlugin（@CapacitorPlugin + call 方法），前端 Capacitor.Plugins.WordDungeon.call 直调 Java 后端 BackendRouter；绕开端口/cleartext/CORS/mixed-content 整条网络链路。LocalServer 保留并委托 BackendRouter。
- **教训**：Android WebView 中前端 JS 与本地服务的 HTTP 通信链路环节多、难调试；App 内首选用原生插件桥（Capacitor registerPlugin），HTTP 仅作浏览器调试通道。

## 19. 借鉴纸片项目：JavaScriptInterface 异步回调桥（V9.1 采用）
- 调研同环境 APK 项目「纸片」（38443854002369026/notes-app，v3→v15 几十个真机版本）：原生自建 WebView + addJavascriptInterface(new Bridge(),"AndroidBridge")，异步回调模式（JS post(callId,method,payload) → 原生 evaluateJavascript("window.__cb(callId,result)")）；数据存 getFilesDir()/library/library.json + SharedPreferences。
- 结论：一加13 等真机上 WebView+JS 桥完全可行，问题不在手机而在通道选择（Capacitor 插件注入时序 / HTTP 链路）。
- 落地：JsBridge 改异步回调桥（callId + __wdCb），前端 jsiCall 封装 Promise + 超时兜底；playwright mock 桥端到端验证 PASS（主页 3674 词）。

## 20. 根因定案：Capacitor 壳在真机的 JS 桥注册时序不可靠 → V9.2 弃用自建 WebView
- 症状链：V8.0 HTTP 链路失败 → V9.0 插件桥时序失败 → V9.1 JS 直连桥仍不可达。
- 核心根因：Capacitor BridgeActivity 的 getBridge()/插件注入/WebView 获取时机在真机不可靠，任何一环失败即整条链路不可用，且无法远程定位（无 logcat）。
- 定案架构（V9.2，纸片项目同构）：MainActivity 弃用 Capacitor，继承 AppCompatActivity 自建 WebView；WebViewClient 拦截 https://app.local/ 返回 assets/public/index.html；addJavascriptInterface(JsBridge) 直连桥（自建即生效）；返回键 onBackPressed → __wdBack；前端 IS_APP 主判 window.WordDungeon；发音用 HTML5 Audio。
- 验证：playwright 无 Capacitor 环境下 JS 桥端到端 PASS（3674 词）。
- 教训：Capacitor 壳在 Android 上引入的"注入/时序/网络策略"黑盒问题难远程排查；纯本地单文件应用直接自建 WebView + addJavascriptInterface 最可靠（纸片 v3→v15 背书）。

## 21. 定案：全原生重构（V10.0）——物理消除"后端不可达"
- V9.2 自建 WebView+JS 桥仍未连通（真机页面可见但数据层不可达，原因不可远程定位）。
- 最终方案：**全原生 Android**。UI 原生 View，后端 Java 引擎内存直调，词库/SQLite 本地直读写，无任何进程间/网络通道——"词书不可用"从物理上不可能出现。
- 教训：本地单进程应用反复引入 Web 容器（Capacitor→自建 WebView）均为过度工程；纯原生直接调库最可靠。

## 22. 无 KVM 沙箱用模拟器定位原生闪退：spacer/row/card 三个 View 根因（V10.0.3）
- **环境搭建（关键经验）**：沙箱无 /dev/kvm，只能 `-no-accel` 软件模拟（x86_64，系统启动约 4-5 分钟，期间反复 ANR 属正常，点 Wait 即可，最终 boot_completed=1）。
  - **文件锁坑**：`/home/user` 挂在 hpvs_fs，QEMU 拿不到镜像写锁（`Failed to get "write" lock`）；`/tmp/user` 是 ext4（锁正常）。把 system-images 真实复制到 /tmp/user/sdk（cmdline-tools/licenses 可符号链接），AVD 建到 /tmp/user/avd。
  - 启动参数：`-no-window -no-audio -no-snapshot -no-boot-anim -gpu swiftshader_indirect -no-accel -netdelay none -netspeed full`，环境变量 ANDROID_HOME/ANDROID_AVD_HOME 指向 ext4。
- **三个确定根因（均为 View 被 addView 两次 / 测量异常）**：
  1. `Ui.spacer` 只设 minHeight 无 LayoutParams → 测量时空 View 撑满剩余高度，主页后续内容全被挤掉（只显示标题）。
  2. `SettingsView.row()` 内部 addView 又返回子 View，调用处再 addView → `IllegalStateException: The specified child already has a parent`。
  3. `AchievementsView.card()` 同型重复 addView。
- **诊断手法**：`adb shell uiautomator dump` 看 View 树与每个节点 bounds，能直接看出"哪个 View 撑满/缺失"；`adb logcat -d | grep 包名` 抓异常栈；`adb exec-out screencap -p > x.png` 截图。
- **教训**：① 自定义 helper 返回的 View 必须明确"谁负责 addView"，禁止内部 addView 又返回；② 任何 addView 的 View 都要给确定的 LayoutParams，不依赖默认值；③ 软件模拟下操作慢于 6 秒计时会大量超时，测试时要快速，或临时调大计时。

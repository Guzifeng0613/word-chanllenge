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

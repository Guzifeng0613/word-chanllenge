# V12.5 原生 UI 迁移验收 · 最终总结

- 验收对象：全原生 Android 版 `android/`（当前 HEAD `68c8739` / V12.7，基线 `420c419` 之后）
- 对照基线：`word-dungeon-demo.html`
- 冻结清单：`docs/agent-flow/v12.5/acceptance-checklist.md`（7 项）
- 报告路径：`round1/report.md`、`round2/report.md`、`round3/report.md`

## 一、三轮执行汇总

| 轮次 | 模拟器状态 | 是否进入应用 | 真实原生截图 | HTML 参考帧 | 结果 |
|------|-----------|--------------|--------------|-------------|------|
| 第1轮 | offline（AVD 从 hpvs_fs 迁到 overlay `/var/tmp/wdavd` 后软件渲染启动） | 否 | 0 张 | 未生成 | 7 项全部 UNVERIFIED |
| 第2轮 | offline（复检，按 hint 不重启） | 否 | 0 张 | 未生成 | 7 项全部 UNVERIFIED |
| 第3轮 | offline（终检） | 否 | 0 张 | 未生成 | 7 项全部 UNVERIFIED |

**核心阻塞**：本沙箱内软件渲染的 x86_64 模拟器在时间预算内始终未完成开机（`getprop sys.boot_completed` 始终为空），无法进入 `com.guzifeng.worddungeon` 截真实原生截图，亦未能起 chromium/playwright 生成 HTML 参考帧做并列对比。为遵守"无真实截图不得判通过"的硬规则，未签发任何 ✅，也不凭空判 ❌。

## 二、7 项最终判定表

| # | 验收项 | 代码级证据（原生源码已实现） | 真实截图 | 最终判定 |
|---|--------|------------------------------|----------|----------|
| 1 | 拼写格子按词长自适应 | 题区居中布局在 `BattleView`；词长→dp 尺寸/间距表待运行实例量取 | 无 | ⚠️ UNVERIFIED |
| 2 | 每5连击全屏横幅+颜色分级 | `showComboBanner()`：`COMBO×N`+副标题；5绿/10金/15橙/20紫；`Sfx.EPIC`+多段震动 `{0,40,30,60,30,80}` | 无 | ⚠️ UNVERIFIED |
| 3 | PERFECT/GOOD 弹字 | `showJudgeGrade()`：拼写≥75%/≥45%，选择≤3s/≤6s；`judgePop()` 金/青上浮 800ms | 无 | ⚠️ UNVERIFIED |
| 4 | 非快刷 1.2s 入场横幅 | `battleIntro()`（层名+emoji+敌人名）战斗开局调用；快刷是否跳过待核 | 无 | ⚠️ UNVERIFIED |
| 5 | 抽卡光柱/击杀闪光 | `RewardDrawView.rarityRay()`：r1 蓝(dp70)/r2 紫(dp90) 底部升起；`BattleView.killFlash()` 白金径向 | 无 | ⚠️ UNVERIFIED |
| 6 | 倒计时最后3秒危机 | `remainSec<=3` 变红 + `dangerPulse`480ms 脉冲 + 每秒 `Sfx.TICK`；>3s整数/≤3s一位小数待核 | 无 | ⚠️ UNVERIFIED |
| 7 | 顶部Toast/词性标签 nowrap | `TopToast`：`TOP|CENTER_HORIZONTAL`、topMargin 12dp、队列+同内容合并、success/error/info 三色；nowrap 待量 | 无 | ⚠️ UNVERIFIED |

汇总：✅ 通过 **0**；❌ 未通过 **0**（无证据不判负）；⚠️ 部分/未验证 **7**。

## 三、未达标项（如实列出）

无业务 UI 项被判定为"未达标 ❌"——因为本轮**没有取得任何真实截图证据**，无法对视觉差距下结论。
真正的未完成项是**环境级阻塞**：模拟器未能在预算内开机，导致 7 项全部停留在"代码已实现、但未经真实截图证实"的 UNVERIFIED 状态。

## 四、声明

- **真机冒烟无法做（沙箱无真机）**：本轮所有验收均依赖 Android 模拟器；当前沙箱无可用真机，真机冒烟测试无法执行。
- 全原生零 WebView、深蓝 `#0d1828` 色系、快刷仅振动无敌人、返回键返回上一级等硬约束，同样因未能进入运行界面而**未做运行时核验**。

## 五、后续补验建议

待模拟器可在预算内开机后：
1. 按 `acceptance-checklist.md` 验收要点，对 2/3/4/5-6/7-8 词长分别截格子尺寸；
2. 用 `adb screenrecord` 抽帧或 DEBUG 隐藏触发入口（不进 release）截连击横幅四档色、PERFECT/GOOD 弹字、入场横幅、r1/r2 光柱、击杀闪光、倒计时红脉冲、顶部 Toast；
3. 对照 `word-dungeon-demo.html` 参考帧逐项把 UNVERIFIED 收敛为 ✅/❌ 并据实修复重编译。

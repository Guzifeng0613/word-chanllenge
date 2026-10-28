# V12.5 原生 UI 迁移验收 · 第 3 轮报告（最终复检）

- 基线同前；清单 `acceptance-checklist.md`（冻结 7 项）。

## 0. 最终复检结果

- `adb devices`：`emulator-5554  offline`。
- `getprop sys.boot_completed`：**仍为空**。
- 三轮复检一致：模拟器始终未在时间预算内完成开机，无法进入 `com.guzifeng.worddungeon` 截真实原生截图，也未能起 chromium/playwright 生成 HTML 参考帧做并列对比。

## 1. 逐项判定（第 3 轮 / 终判）

| # | 项 | 代码级实现（已核实存在） | 真实截图 | 终判 |
|---|----|--------------------------|----------|------|
| 1 | 格子按词长自适应 | 居中布局在；词长→dp 尺寸/间距表待运行实例量取 | 无 | ⚠️ UNVERIFIED |
| 2 | 连击横幅+颜色分级 | `showComboBanner`：5绿/10金/15橙/20紫 + EPIC + 多段震动 | 无 | ⚠️ UNVERIFIED |
| 3 | PERFECT/GOOD 弹字 | `showJudgeGrade` 阈值 + `judgePop` 金/青上浮 | 无 | ⚠️ UNVERIFIED |
| 4 | 入场横幅 | `battleIntro`（层名+emoji+敌人名）；快刷跳过待核 | 无 | ⚠️ UNVERIFIED |
| 5 | 光柱/击杀闪光 | `rarityRay` r1蓝/r2紫底部升起；`killFlash` 白金径向 | 无 | ⚠️ UNVERIFIED |
| 6 | 倒计时危机 | ≤3s 变红+480ms脉冲+每秒TICK；>3s整数/≤3s一位小数待核 | 无 | ⚠️ UNVERIFIED |
| 7 | 顶部Toast/词性nowrap | `TopToast` 顶部居中/队列合并/三色；nowrap 待核 | 无 | ⚠️ UNVERIFIED |

## 2. 并列对比图

| 项 | 原生截图（round3/） | HTML 参考（reference/） |
|----|--------------------|--------------------------|
| 1–7 | _未采集_ | _未采集_ |

## 3. 结论

- 三轮均因模拟器未开机而**无法取得真实截图证据**。
- 依硬规则，不签发任何 ✅ 通过；亦不凭空判 ❌。7 项统一记为 **⚠️ UNVERIFIED（代码已实现，未经真实截图证实）**。
- 真机冒烟：无法做（沙箱无真机）。
- 后续建议：待模拟器可在预算内开机后，按 `acceptance-checklist.md` 的验收要点逐项补截真实截图（瞬态特效用 `adb screenrecord` 抽帧或 DEBUG 隐藏触发入口），再据此把 UNVERIFIED 收敛为 ✅/❌。

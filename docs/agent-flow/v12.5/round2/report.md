# V12.5 原生 UI 迁移验收 · 第 2 轮报告

- 基线同第 1 轮；清单 `acceptance-checklist.md`（冻结 7 项）。
- 本轮动作：在第 1 轮把 AVD 迁到 overlay（`/var/tmp/wdavd`）并以软件渲染启动后，再次复检设备。

## 0. 复检结果

- `adb devices`：`emulator-5554  offline`（与第 1 轮一致，未进入 device/online）。
- `getprop sys.boot_completed`：**仍为空**，从未返回 `1`。
- 日志停留在 `Emulator is performing a full startup`，无新错误、也无 `sys.boot_completed` 信号——软件渲染 x86_64 在本沙箱内启动过慢/卡死，未在预算内进入可截图状态。
- 按 hint"软件渲染下不要重启模拟器"，本轮未重启模拟器，仅复检。

## 1. 逐项判定（第 2 轮）

仍无法进入应用截真实原生截图。依硬规则"无真实截图不得判 ✅"，7 项维持 ⚠️ UNVERIFIED：

| # | 项 | 本轮新证据/动作 | 真实截图 | 判定 |
|---|----|-----------------|----------|------|
| 1 | 格子按词长自适应 | 未取得运行实例，无法量像素 | 无 | ⚠️ UNVERIFIED |
| 2 | 连击横幅+颜色分级 | 代码已存在（`showComboBanner` 四级色+EPIC+震动），未视觉复核 | 无 | ⚠️ UNVERIFIED |
| 3 | PERFECT/GOOD 弹字 | 代码阈值 0.75/0.45、≤3s/≤6s 已在，未视觉复核 | 无 | ⚠️ UNVERIFIED |
| 4 | 入场横幅/快刷不打断 | `battleIntro` 已在，未视觉复核快刷是否跳过 | 无 | ⚠️ UNVERIFIED |
| 5 | 光柱/击杀闪光 | `rarityRay` r1蓝70/r2紫90、`killFlash` 已在，未视觉复核 | 无 | ⚠️ UNVERIFIED |
| 6 | 倒计时3秒危机 | `remainSec<=3` 变红+480ms脉冲+TICK 已在，未视觉复核 | 无 | ⚠️ UNVERIFIED |
| 7 | 顶部Toast/词性nowrap | `TopToast` 顶部居中+队列合并+三色已在，nowrap 未量 | 无 | ⚠️ UNVERIFIED |

## 2. 并列对比图

| 项 | 原生截图（round2/） | HTML 参考（reference/） |
|----|--------------------|--------------------------|
| 1–7 | _未采集_ | _未采集_ |

## 3. gap / 修复 / 重验

- gap：环境级（模拟器 offline），非业务 UI gap。
- 修复：无新代码改动（无界面即无客观视觉 gap 可修）。
- 重验：第 3 轮做最终一次复检；若仍 offline，验收在"代码已实现但未经真实截图证实"状态下封存，并如实声明。

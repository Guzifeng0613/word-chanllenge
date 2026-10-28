# V12.5 原生 UI 迁移验收 · 第 1 轮报告

- 验收对象：全原生 Android 版 `android/`（当前 HEAD `68c8739`，即 V12.7；基线提交 `420c419` 之后）
- 对照基线：`word-dungeon-demo.html`（含 V12.0/V12.1 UI 提交）
- 清单：`docs/agent-flow/v12.5/acceptance-checklist.md`（冻结 7 项）
- 本轮执行时间：2026-10-27（Asia/Shanghai）

## 0. 环境与阻塞声明（关键）

按 agent-hint，验收前须先在已开机的模拟器上截真实原生截图。本轮执行时发现：

- 进程检查时模拟器**未在运行**（无 qemu 进程、`adb devices` 为空）。
- AVD 原位于 `/home/user/.android/avd/wdtest.avd`，而 `/home/user` 挂载为 `hpvs_fs`（已 `mount` 核实），正是 hint 警告的"不支持 qemu 数据盘 resize/写锁 → /data 0 字节、开机 recovery"陷阱分区。
- 已按正确做法把 AVD 整体复制到 overlay 数据盘 `/var/tmp/wdavd/wdtest.avd`（`/var/tmp` 在 root overlay 上，10GB 可写），并改写 `wdtest.ini` 指向新路径。
- 用历史参数启动：`emulator -avd wdtest -no-snapshot -no-audio -no-boot-anim -no-window -gpu swiftshader_indirect -accel off`（软件渲染），`ANDROID_AVD_HOME=/var/tmp/wdavd`。
- 启动日志显示 `setDisplayActiveConfig: 1080x2400`、`Emulator is performing a full startup`，但在剩余时间预算内：
  - `adb devices` 设备状态恒为 **`offline`**；
  - `getprop sys.boot_completed` **始终为空**（从未返回 `1`）。

**结论：模拟器在本轮预算窗口内未能完成开机，无法对任何一项截真实原生截图，也无法生成 HTML 参考帧做并列对比。**

> 硬规则要求"禁止只凭代码推断就判通过，必须有真实截图"。在没有真实截图的前提下，本轮**不对任何一项给出 ✅**。下表中每项给出：代码级实现证据 + 视觉判定状态（UNVERIFIED=未取得真实截图）。

## 1. 逐项判定（第 1 轮）

| # | 验收项 | 代码级证据（原生源码） | 真实截图 | 判定 |
|---|--------|------------------------|----------|------|
| 1 | 拼写格子按词长自适应 | `BattleView` 中题目/格区居中（多处 `Gravity.CENTER`）；具体按词长的 dp 尺寸表需在可运行界面上量取，**未能在运行实例上量到实际像素** | 无 | ⚠️ UNVERIFIED |
| 2 | 每5连击全屏横幅+颜色分级 | `BattleView.showComboBanner()`（~2360 行）：`COMBO ×N` 大字 + 副标题；分级 `n>=20→PURPLE势不可挡 / >=15→ORANGE输出爆表 / >=10→GOLD连击高手 / else GREEN手感火热`；触发 `Sfx.play(EPIC)` + `Ui.vibrate({0,40,30,60,30,80})`；800ms 后淡出 | 无 | ⚠️ UNVERIFIED |
| 3 | PERFECT/GOOD 弹字 | `showJudgeGrade()`（~2325）：拼写 `frac>=0.75→PERFECT / >=0.45→GOOD`；选择 `elapsed<=FAST_T→PERFECT / <=NORMAL_T→GOOD`；`judgePop()`（~2342）PERFECT金/GOOD蓝，上浮 800ms | 无 | ⚠️ UNVERIFIED |
| 4 | 非快刷 1.2s 入场横幅 | `battleIntro()`（~2297）在战斗开始调用（~469 行）；快刷路径是否跳过需在运行实例确认 | 无 | ⚠️ UNVERIFIED |
| 5 | 抽卡光柱 / 击杀闪光 | `RewardDrawView.rarityRay()`（~100）：`w = rare>=2?dp(90):dp(70)`；`color = rare>=2?0xCCC084FC紫:0xAA38BDF8蓝`；自底部 `pivotY=getHeight()` 升起，300ms 展开 + 1000ms 淡出；`BattleView.killFlash()`（~2279）击杀调用（~1533） | 无 | ⚠️ UNVERIFIED |
| 6 | 倒计时最后3秒危机 | `BattleView`（~2027）：`danger = remainSec<=3`；`dangerPulse`（~1963）480ms 缩放脉冲；`Sfx.play(TICK)` 每秒一次；`color = danger?RED:MUT` | 无 | ⚠️ UNVERIFIED |
| 7 | 顶部 Toast / 词性标签 nowrap | `TopToast.java`：`gravity=TOP|CENTER_HORIZONTAL`、`topMargin=dp(12)`；队列 `queue` + 同内容 `return` 合并；配色 success 0xFF14B8A6 / error 0xFFEF4444 / info；词性标签 nowrap 需在界面量取 | 无 | ⚠️ UNVERIFIED |

## 2. 并列对比图

本轮**无原生截图、无 HTML 参考截图**（两侧均因模拟器/浏览器未在预算内就绪而缺位）。对比占位说明：

| 项 | 原生截图（round1/） | HTML 参考（reference/） |
|----|--------------------|--------------------------|
| 1–7 | _未采集_ | _未采集_ |

## 3. 差距（gap）与修复动作

- gap：环境级阻塞——模拟器 `offline`，无法进入应用。
- 已做修复/规避：把 AVD 从 hpvs_fs 迁到 overlay（`/var/tmp/wdavd`），规避已知数据盘写锁陷阱；用软件渲染参数启动。
- 未做代码修复：因未取到任何界面，无法定位真实视觉 gap，不盲目改业务代码。
- 重验：见第 2 轮（再次检查设备是否完成开机）。

## 4. 本轮小结

- 通过 ✅：0
- 未通过 ❌：0（无证据，不判负）
- 部分/未验证 ⚠️：7（全部因模拟器未开机、无真实截图）
- 真机冒烟：无法做（沙箱无真机）。

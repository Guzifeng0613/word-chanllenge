# 迭代 2：倒计时与战斗节奏流畅度（V11.9 R2）

- 基线：cc86b84（R1）→ 提交 b57b5ab（短哈希），改动文件仅 `ui/BattleView.java`、`ui/HpBar.java`、`ui/AmbientBackground.java`
- 闭环：验收标准定稿 → 诊断差距（战斗反馈审计）→ 实现（agent R2实现）→ 独立编译 → 独立复核（agent R2复核）
- 状态：**通过（7/7）**，已进入 R3

## 验收清单与判定

| # | 验收项（代码级） | 判定 | 证据锚点 |
|---|---|---|---|
| 1 | 计时渲染去冗余：timerTxt 仅文本/颜色变化时 setText；setTimerFill target 未变跳过、LayoutParams 复用；100ms tick 结算与答题判定零改动 | ✅ | setTimerText:1749-1758；setTimerFill:1810；applyFillWidth:1824-1831；remainSec 递减:1779 零改动 |
| 2 | 切题过渡：题干/答案区 180ms 淡入+6dp 上移；答对 350ms/答错 1100/1600ms 等待时长未变 | ✅ | quizEnterTransition:781-792；:1171/:1694 未变 |
| 3 | 新怪入场：先 cancel idleAnim、复位 scale/alpha，230ms 淡入上浮，入场结束交回 idleFloat | ✅ | spawnEnemy:422-438；idleFloat:1870 |
| 4 | 升级字卡：总展示 1300+400→320+600+260=860ms（≤900ms），反馈仍清晰 | ✅ | onLevelUp:2004/2009/2011 |
| 5 | 血条受击闪白：HpBar.flashHit 180ms 衰减，与 setRatio 160ms 平滑独立；怪物/玩家受击均接入 | ✅ | HpBar:50-61/30-42；BattleView:1206/1252 |
| 6 | AmbientBackground 零每帧分配：Paint/shader 缓存，渐变按高度/辉光按半径重建，translate/scale+setAlpha 呼吸，视觉等价 | ✅ | :29-30、:75、:86、:109-118 |
| 7 | 编译 assembleDebug BUILD SUCCESSFUL（独立强制 --rerun-tasks 两次全通过，108 tasks 全执行） | ✅ | 复核实测 |

边界核查：diff 仅 3 个 UI 文件；versionName/Ui 色板/server/store/core/docs 未变；R1 心流闭环（triggerFlow/applyComboDots/collapseComboDots/comboSubText/dissolveEnemy/ringBurst 青色/550ms 浮层）方法与调用链完整保留；answerArea 淡入与选项滑入 alpha 双渐升无隐藏；spawn 入场与消散/idleFloat 衔接无残留。

## 真机行为（预期描述，沙箱无真机，待用户上机确认）

战斗中倒计时数字滚动无闪烁/无 10fps 台阶感；切题时新题淡入上浮，无硬切；击杀→新怪淡入入场衔接自然；升级字卡快速弹出快速退场，新题立即可读；怪物/玩家受击瞬间血条闪白后平滑回落；长时间游玩背景无卡顿（GC 压力下降）。

## 剩余差距 / 观察项（不阻塞）

1. 未真机实测：动画时长（180/230/860ms）与血条闪白强度（峰值约 74% 白叠层）为代码层规格内取值，**建议用户上机抽测闪白强度**，若过强可微调（非阻断）。
2. R2-1 保留 ValueAnimator 按 tick 重建（像素目标每 100ms 仍变化），已消除每帧 new LayoutParams 与无谓 setText——在不改计时结算前提下的最大去冗余。
3. R2-6 辉光边缘近全透→全透，实测差异约 0.13% 不透明度，亚像素等价。

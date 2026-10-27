# 迭代 1：连击心流闭环与打击反馈（V11.9 R1）

- 基线：be872d1（V11.8）→ 提交 cc86b84（短哈希），仅改动 `android/app/src/main/java/com/guzifeng/worddungeon/ui/BattleView.java`（+109/−20）
- 闭环：验收标准定稿 → 诊断差距（战斗反馈审计）→ 实现（agent R1实现）→ 独立编译 → 独立复核（agent R1复核）
- 状态：**通过（8/8）**，已进入 R2

## 验收清单与判定

| # | 验收项（代码级） | 判定 | 证据锚点 |
|---|---|---|---|
| 1 | 连击槽第 1~4 格点亮带 scale 脉冲弹动（≤200ms），不再哑亮 | ✅ | BattleView.java:1264-1268（i<4 抑制第5格，180ms） |
| 2 | 答错触发"红闪→塌缩清空"（≤350ms）后回未点亮态；comboNum 同步回落；不阻塞切题 | ✅ | :1072-1078、:1276-1297（≤330ms）；pauseThenNext:1130 保留 |
| 3 | 满 5 整槽一次闪光（不再循环内逐格重复）+ ringBurst 青色 + 心流横幅 + 上行琶音同帧 | ✅ | triggerFlow:1300-1312；循环内旧闪光已删 |
| 4 | ringBurst 颜色入参化：心流传 #2DD4BF，无参重载保紫，未改 Ui 色板 | ✅ | :2004/:2006-2019；Ui.TEAL=0xFF2DD4BF |
| 5 | combo≥6 心流期槽位保持满格高亮，comboSub 显示状态文案不留白 | ✅ | :1259-1262、comboSubText:1315-1323 |
| 6 | 里程碑 10/20/30 与既有 5/8 档共存，阶梯降序无死分支 | ✅ | comboSubText:1316-1322 |
| 7 | 击杀消散动画（~300ms）+ 粒子比暴击更盛（22金+14青 vs 暴击单层14）+ 层间浮层延迟 380→550ms | ✅ | dissolveEnemy:1371-1377；:1198/1340-1341；:1366-1367 |
| 8 | 编译 assembleDebug BUILD SUCCESSFUL（独立强制重跑编译验证，零新增错误） | ✅ | `compileDebugJavaWithJavac --rerun-tasks` 通过 |

边界核查：diff 仅 BattleView.java；Ui.java/Sfx.java/HpBar/versionName/docs/server/store/core 均未变；答错 flowOn 回落与 pauseThenNext 语义保留；计时/答题主流程未动。

## 真机行为（预期描述，沙箱无真机，待用户上机确认）

连续答对 5 题：槽格逐个弹动点亮；满 5 瞬间整槽闪光 + 青色圆环扩散 + "心 流"横幅 + 上行琶音。答错瞬间：槽格红闪逐格塌缩清空、连击数回落。击杀时：敌人面部消散 + 金/青双层粒子爆发，随后再盖层间结算浮层。连击 10/20/30 时出现"势如破竹/势不可挡/登峰造极"里程碑文案；心流期连击槽满格高亮不消失。

## 剩余差距 / 可选观察（不阻塞）

1. 无参 `ringBurst()` 重载（:2004）成为无调用方保留代码（可后续清理）。
2. `onDetachedFromWindow` 未统一清 main 回调与 fillAnim（当前因 exitToHome `removeCallbacksAndMessages(null)` 兜底 + 回调防御式写法无实际危害）——**纳入 R3 稳定性兜底**。
3. 真机视觉走查未做（沙箱限制），动效时长已按验收上限取值。

# gap-report-v2.md —— 第 2 轮复审差距清单（Agent5 独立复核）

> 复核方式：不采信 Agent2/3 自报，全部状态以代码事实为准。
> 被审提交：4866235（后端 R2）、60fe2db（前端 R2），HEAD=60fe2db。
> 编译抽查：`./gradlew assembleDebug` **BUILD SUCCESSFUL（exit 0，9s）**。

---

## 0. 总览

- R1 的 GAP-01~14：**已关闭 9 ｜ 部分 4 ｜ 未关闭 1**。
- Agent5 补充 ACC-40~44：**已关闭 2 ｜ 部分 3**。
- 剩余需修复：R2 新发现/未闭环 4 项（P1×3、P2×1）。
- **收敛判断**：主链路（词库/出题/SRS/快刷/错词本/成就/界面/编译）已全部闭环；剩余 3 个 P1 集中在**经济账本、卡效果语义、微目标前后端口径**，建议做**第 3 轮小修**（预计半天工作量）后即可验收结束。

---

## 1. GAP-01~14 逐条复核

| 条目 | R1 结论 | R2 复核状态 | 代码事实 |
|---|---|---|---|
| GAP-01 25 词典卡+pity+效果 | ❌ | 🟡 **部分关闭** | 见 §2-A |
| GAP-02 souls 持久化 | ❌ | 🟡 **部分关闭** | 后端已持久化；冷启读取/账本双轨见 §2-B |
| GAP-03 enemy.goal 后端下发 | ❌ | 🟡 **部分关闭** | 后端已下发；前端未消费，见 §2-C |
| GAP-04 拼写题永不出现 | ❌ | ✅ **已关闭** | QuizEngine.draw:141-144 与 QuickEngine.draw:177-178 均在非 spell 题后 `runState.exposed.put(word,true)`，随 run_state 回传，guardSpell 解锁链路打通 |
| GAP-05 cards 不上报 | ❌ | ✅ **已关闭** | ResultView.onFinish 发送 `cards=act.runCards`；BattleView 翻牌选卡/商店购卡均 runCards++ |
| GAP-06 master30/60 无解锁 | ❌ | ✅ **已关闭** | BackendRouter.progressFinish 加 `store.masteredCount()` >=30/60 幂等解锁 |
| GAP-07 enemy_confuse 不发送 | 🟡 | ✅ **已关闭** | BattleView.drawReq 在 tpl.id=="confuser" 时下发 `enemy_confuse=true` |
| GAP-08 词书/难度/快刷档不持久 | 🟡 | ✅ **已关闭** | MainActivity.loadPrefs() 冷启读 wd_prefs；Settings/Home 改动后 savePrefs() 写回 |
| GAP-09 凝神+2s 未证实 | 🟡 | ✅ **已关闭** | 连错≥2 → bonusTime+=2（BattleView:972-973）；下题 timeLimit+=bonusTime 后清零（:1450-1451） |
| GAP-10 浮层返回被吞 | 🟡 | ✅ **已关闭** | overlayView 记录翻牌/泉/商店；onBack 先关浮层并推进流程（抽卡→泉、泉→商店、商店→advanceLayer），无浮层才弹退出确认 |
| GAP-11 零散数值/泉排序 | 🟡 | ✅ **已关闭** | SpringView 按 layerWrong 出错次数降序（≈顽固>易错>生疏）；商店祝福 pendingBless 跨层+25% 保留 |
| GAP-12 JsBridge 死代码 | ❌ | ✅ **已关闭** | bridge/JsBridge.java 整文件删除（-86 行） |
| GAP-13 ⓘ tipbox 用 Toast | 🟡 | ❌ **未关闭（P2 可接受）** | SettingsView 仍 Toast 长提示，未做 tipbox 弹窗。视觉等价，不阻断 |
| GAP-14 结算新学词口径 | 🟡 | ✅ **已关闭** | 统计格改用 act.runNewWords.size()，与 chips 同源 |

## 2. 剩余差距明细

### A. R2-A（P1）：词典卡战斗效果未按 V7.0 语义接线（GAP-01 残留）
- 现状：`core/CardCatalog.java` 25 卡目录完整（9 普通/9 稀有/7 史诗，名称/描述与 V7.0 一致）；`BackendRouter.cardDraw` 三槽 + pity>=3/epicPity>=6 保底正确；`/shop/buy` 真实扣魂；翻牌演出低→高、史诗压轴保留。**但** `BattleView.applyCard()` 只把 12 个卡 id 映射为通用增益（回血/伤害倍率/时间+2），其余走 default 按稀有度给温和增益。
- 与目标定义不符处（对照 agent1-summary）：
  - **灵光乍现 flash / 暴击风暴 critstorm / 致命一击 deadly**：目标是**暴击率**（+15%/+25%/暴击×3），现状统一压成 `bonusDamageMult=1.4`——暴击系统完全没接线；BattleView 暴击仍是本地规则 `combo%5==0`。
  - **势如破竹 unstop**（答错连击减半不清零）、**错词护盾 shield**（开局 6 盾）、**词魂引力 soulmagnet**（胜利词魂+40%）、**汲词 lifesteal**（答对回血=伤害10%）、**不朽 immortal**（免死一次）等机制型卡均未实现，落入 default。
- Agent2 自报"完整数值映射待后端 effect 文案细化"——**不成立**：V7.0 卡效果定义在 agent1-summary 中已是确定值，目录里 desc 也写明了；缺的是前端把 id 映射进 DamageCalc 入参（critChance/critMult/comboHalve/shield 等）。
- 修复建议（前端）：BattleView 持有 `cardFlags`（critBonus/comboHalved/shield/lifestealPct 等），答题时 DamageCalc.calc 增加入参；至少把 flash/critstorm/deadly/unstop 四张关键卡先接上。
- 涉及文件：`ui/BattleView.java` applyCard/crit 计算处、`core/DamageCalc.java`。

### B. R2-B（P1）：词魂双账本 + 冷启不读余额（GAP-02 残留，组织者专项 a）
- 后端事实：users.souls 列已加（DB_VERSION 5，onUpgrade try/catch 幂等）；`/stats/daily`、`/answer`、`/progress/finish` 均返回 souls；无独立 GET /shop/souls 路由。
- 前端事实：
  1. MainActivity.souls 冷启=0，**全 App 无任何地方把 stats.souls 读回 act.souls**（HomeView.refreshMeta 未读 souls 字段）→ 冷启词魂显示归零（与 Agent2 预警一致）。
  2. BattleView:333 每局（非快刷）`act.souls=0` 重置，击杀时内存 `+1/+2`（:920/:1095）——这是**前端展示账本**；而 `/shop/buy` 扣的是**后端 DB 账本**（progressFinish 在局末一次性 +15/35/60）。两本账从不对账：UI 显示击杀累计魂数，服务端余额是另一个数；层间商店可能出现"UI 有钱、服务端 souls_not_enough"或反之。
- 严重度：**P1**（经济显示与实际余额错配，商店购买会莫名失败；不崩溃、不阻断战斗主循环，故不定 P0）。
- 修复建议：
  1. 新开战斗/进入商店前 `act.souls = stats.daily.souls`（或后端加 `GET /shop/souls` 专路由），去掉每局清零；
  2. 击杀词魂改为每次击杀后调用 `/reward/xp` 同款轻路由（或 answer 响应返回 souls 余额，前端直接采用），不再内存累加。

### C. R2-C（P1）：前端微目标不消费后端 goal（GAP-03 残留）
- 后端：enemySpawn 下发 `goal ∈ {streak4, hit5, flawless}`。
- 前端：`BattleView.pickGoal()` 仍本地随机 `{combo4, correct5, flawless}`（:423-429），**完全没读 enemy.optString("goal")**；且命名不一致（streak4 vs combo4）。微目标实时进度框（refreshGoal 进行中/已达成）本身是好的。
- 修复建议：pickGoal 改为读后端 goal 并做命名映射（streak4→连击4 / hit5→答对5 / flawless→无错）。

### D. R2-D（P2，可延后）：GAP-13 tipbox 仍 Toast。

## 3. Agent5 补充项 ACC-40~44 复核
| 编号 | 状态 | 事实 |
|---|---|---|
| ACC-40 souls 持久化+真实扣减 | 🟡 部分 | DB 持久化与 /shop/buy 扣减已闭环；冷启读取与双账本残留=R2-B |
| ACC-41 微目标下发+实时进度 | 🟡 部分 | 后端下发与进度 UI 已就位；前端未消费后端值=R2-C |
| ACC-42 拼写题实际出现 | ✅ 已关闭 | exposed 写入点已补（见 GAP-04） |
| ACC-43 删 JsBridge 死代码 | ✅ 已关闭 | 文件已删 |
| ACC-44 词书/难度/快刷档冷启保留 | ✅ 已关闭 | wd_prefs 读写 |

## 4. 专项 a/b/c 结论
- **a 词魂冷启读取**：未提供专路由，但 /stats/daily 已带 souls——**真正问题不是缺路由，而是前端从不读 + 前端内存账本与 DB 账本双轨**。严重度 P1，修复见 R2-B。
- **b 卡效果接线**：**不达标**。目录/演出/经济已对齐 V7.0，但机制型卡（暴击率/连击/护盾/吸血/免死）未接线，暴击类卡被拍平成伤害倍率。见 R2-A。
- **c 抽卡三槽**：✅ 一致。/card/draw 返回 slots+pity/epicPity；RewardDrawView 按 rare 升序 autoReveal、史诗压轴金光；刷新走 /shop/buy reroll，**仅 ok=true 才重建浮层扣魂成功**，失败弹"词魂不足"不扣（BattleView.rerollRewardDraw 已核）。

## 5. 回归与编译
- 新接线未破坏既有流程：draw/answer/wrongbook/settings 链路完好；DB_VERSION 4→5 迁移幂等。
- 编译：assembleDebug BUILD SUCCESSFUL（HEAD=60fe2db）。

## 6. 收敛判断
- **可结束项**：词库/出题引擎/SRS/快刷/错词本/主页/设置/结算/翻牌演出/错词本/发音/返回键/编译。
- **需第 3 轮修复**：R2-A（卡效果语义接线，P1）、R2-B（词魂账本统一，P1）、R2-C（微目标读后端值，P1）；R2-D（tipbox，P2，可随下轮顺手做或接受现状）。
- 预计 R3 闭环后 P0 清零、P1 清零，即可交付。

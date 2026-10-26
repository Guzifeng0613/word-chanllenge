# gap-report-v3.md —— 第 3 轮（最终）复审差距清单

> 复核方式：状态只认代码事实。被审提交：9f92ca2（后端 R3）、07df830（前端 R3），HEAD=07df830。
> 编译终检：`./gradlew assembleDebug` **BUILD SUCCESSFUL（exit 0，10s）**。

---

## 0. 总览
- R2-A/R2-C：✅ 已关闭。
- R2-B：🟡 **部分关闭**——对账读取链路已通，但**击杀入账的 kill 标志前端从未发送**，DB 词魂钱包实际始终为 0，商店不可用（唯一残留 P1，一行级 wiring 缺陷）。
- 版本号：versionName=11.7、versionCode=24 ✅。
- 硬约束终检：全部通过（含残留说明）。

---

## 1. R2-A/R2-B/R2-C 逐条复核

### R2-A 卡效果语义接线 —— ✅ 已关闭
- 暴击链路：`BattleView` 持有 `critChance`（flash +0.15 / critstorm +0.25 / deadly +0.10 且 critMult=3.0）；答题时本地掷骰 `crit = critChance>0 && Math.random()<critChance`，把 `crit` + `crit_mult` 提交 /quiz/answer；后端 answer 再兜底掷一次（`!crit && critChance>0` 时）——双保险一致，DamageCalc 消费 crit/critMult ✅。
- shield：`hitPlayer` 优先扣盾 6 点并 feed ✅。
- lifesteal：答对按 `damage×10%` 回血（后端同步返回 heal_back，前端自算一致）✅。
- immortal：致命伤以 1 HP 存活一次（`immortalUsed` 幂等）✅。
- unstop：答错连击 `combo/2` 而非清零 ✅；soulmagnet 胜利词魂×1.4 ✅。
- 25 卡中 21 张有 id 专属接线，其余 4 张见 §2。

### R2-B 词魂对账 —— 🟡 部分关闭（残留 P1，见 §3-R3-1）
- /shop/souls 路由：**后端确实加了**（9f92ca2，`shopSouls()` 返回 DB 余额）。前端实际未调它，而是走 /stats/daily（HomeView 冷启读回 souls）+ answer 响应 souls 刷新——**两者同源 DB，不冲突**，Agent2/3 各自说法都对。
- 但：**击杀即时入账未闭环**——answer body 从未发送 `kill`/`kill_kind`（BattleView 答题请求体无此二字段），后端击杀入账分支是死代码；finish 又已停止发放。结果：DB 钱包恒为 0 → 商店购买恒 `souls_not_enough`；且前端击杀 `act.souls+=60` 后，下一题 answer 响应把 act.souls 覆盖回 DB 值（0），词魂数字闪烁归零。

### R2-C 微目标 —— ✅ 已关闭
- `pickGoal` 改读 `enemy.optString("goal")`，命名映射 streak4→连击4 / hit5→答对5 / flawless→无错；缺省本地兜底；`refreshGoal` 同步新命名。后端下发与前端消费一致。

## 2. Agent2 列出的 5 张"需后端结算"卡
| 卡 | 后端 R3 是否联动 | 结论 |
|---|---|---|
| timectrl（限时+3/超时受击减半） | 未联动；前端近似 bonusTime+=3 | 🟢 接受近似（方向正确） |
| spellchain（下题强制拼写+50%） | 未联动；前端近似伤害×1.2 | 🟢 接受近似 |
| glasscannon（伤害×1.8 但 HP-30） | 未联动；前端近似伤害×1.5（未减 HP） | 🟢 接受近似 |
| wordfire（每连对4题 8% 真伤） | 未联动；前端近似伤害×1.3 | 🟢 接受近似 |
| immortal（免死一次） | 后端无需校验，前端 hitPlayer 已正确接线 | ✅ 功能闭环 |

**结论**：4 张史诗卡为展示侧数值近似，效果方向与 V7.0 一致、不阻断主循环（战斗/学习/错词/成就全部可用），作为已知后续优化项可接受；immortal 已完整功能化。

## 3. 残留差距（仅 1 条 P1）
- **R3-1（P1）击杀词魂未写 DB，商店经济实际不可用**
  - 文件：`ui/BattleView.java` 答题请求体（约 896-920 行）缺 `kill`/`kill_kind`；`server/BackendRouter.answer` 击杀入账分支因此为死代码。
  - 修复（一行级）：击杀命中（敌人 hp 归 0 那道题）的 answer body 补 `body.put("kill", true)` + `body.put("kill_kind", boss/elite/normal)` + champion。后端即可即时入账 DB，前端 answer 响应 souls 自动对账，商店恢复可用。

## 4. 硬约束终检
- 零 WebView：✅ 运行时无 WebView/JsBridge 实例；`bridge/JsBridge.java` 已删。残留：`bridge/WordDungeonPlugin.java`（Capacitor 插件壳，无人实例化，未 import WebView）——P2 清理项，不阻断。
- 零网络：✅ 无任何 http 调用；发音离线 TTS。
- 返回键链：✅ 主页/子页/战斗确认/浮层先关浮层。
- 拼写交互：✅ 回车确认/不反向/删除键/答错显正确答案。
- 计时小数：✅ 最后 3 秒一位小数。
- 先见后拼：✅ exposed 写入+guardSpell+run_state 回传链路完整。

## 5. 收敛判断
- 编译通过；P0 全部清零；R2-A/C 及 90% 功能闭环。
- 剩余唯一 P1（R3-1）是一行级 wiring 遗漏，不是设计/架构问题。
- **建议**：补 R3-1 一行修复后即达可交付（预计 10 分钟，复审可走轻量确认）。若组织者判断商店词魂经济为次级功能、时间紧迫，亦可带此已知缺陷交付并列入后续迭代——主学习/战斗/错词/成就闭环不受影响。

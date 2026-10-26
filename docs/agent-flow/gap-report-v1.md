# gap-report-v1.md —— 第 1 轮迭代审核差距清单（Agent5 独立核对）

> 审核方式：不采信 Agent2/3 自报结论，全部状态以 `git diff e2c2317..HEAD` + 实际源码为准。
> 基线 commit：e2c2317（V11.6）；被审 commit：25d189c（前端）、be58a10（后端），当前 HEAD=be58a10。
> 编译抽查：`./gradlew assembleDebug` **BUILD SUCCESSFUL（exit 0）**。

---

## 0. 总览统计

- 核对总数：**39 条**（总则 4 项并入对应条核查，不单列）。
- 通过 **25** ｜ 部分通过 **9** ｜ 未通过 **5**。
- 按优先级分布：
  - **P0 未通过/部分**：GAP-01（ACC-18，25 词典卡+pity）、GAP-02（专项 a：souls 持久化缺失）、GAP-03（专项 b：enemy.goal 后端不下发）、GAP-04（专项 e：拼写题永不出现）、GAP-05（cards 数不上报 → cards5/10 成就死锁）、GAP-06（master30/60 成就无解锁逻辑）。
  - **P1 未通过/部分**：GAP-07（enemy_confuse 前端不下发）、GAP-08（词书/难度/快刷档不持久）、GAP-09（凝神 +2s 是否真生效存疑）、GAP-10（层间节点浮层返回键被战斗退出确认吞掉）、GAP-11（SPELL 速度档/商店祝福等数值接线待补）。
  - **P2**：GAP-12（bridge/JsBridge.java 死代码仍 import WebView）、GAP-13（ⓘ tipbox 用 Toast 代替）、GAP-14（结算"新学词"数字=questions-wrong 启发式，与 chip 列表不一致）。

---

## 1. 专项核查结论（组织者点名 a~f）

### a. 词魂 souls 持久化 —— ❌ 未做（缺口在后端 Agent3）
- 事实：`MainActivity.java:47` `public int souls = 0;` 注释自报"持久化依赖 Agent3 后端字段，前端先本地维护"。
- 后端核查：`ProgressStore` 的 users/meta 表无 souls/gold 列；`BackendRouter` 无 `/shop/buy`、无 souls 读写接口；`BattleView` 每层 `act.souls` 仅内存累计，且远征每局开始清零（非快刷）。
- 结论：**词魂既不跨局持久化，也无后端字段**。与 Agent2 自报一致，缺口真实存在。
- 修复建议（后端）：meta JSON 增加 `souls` 字段（或 users 表加列）；新增 `POST /shop/buy`（item、price 校验、扣 souls、返回余额）、`GET /shop/souls`；前端 ShopView/RewardDrawView 的扣减改为接口调用。

### b. 微目标 enemy.goal —— ❌ 后端未下发（缺口在后端 Agent3）
- 事实：`BackendRouter.enemySpawn` 返回信封字段为 tpl/name/maxHp/hp/atk/boss/elite/champion/quick，**无 goal 字段**。
- 前端 `BattleView.pickGoal()`（约 410 行）：本地字符串数组 `goals = {...}` 随机取一条展示，与当前敌人模板无任何关联。
- 结论：微目标是纯展示假数据。与 Agent2 自报一致。
- 修复建议（后端）：`enemySpawn` 按 tpl 下发权威 goal 文案（如 confuser="本场至少答对 3 道易混题"），放入 enemy JSON；前端改为读 enemy.goal 并做实时进度计数（v1 未列该计数，见 Agent5 补充 ACC-41）。

### c. 25 词典卡 + pity 保底（ACC-18/19）—— ❌ 两端都没做（两端共有缺口）
- 前端事实：`RewardDrawView.rollPool()` 只有 **9 个通用奖励**（heal/xp/damage/time × 三档稀有度），**不是 25 张词典卡**；无 pity/epicPity 计数；"刷新一组·25 词魂"按钮**不真扣 souls**（`refreshed=true` 即止）；抽到的卡除 heal/xp/damage/time 外**无战斗效果**（灵光乍现 +15% 暴击、暴击风暴 +25%、致命一击 ×3、势如破竹全部不存在）。
- `ShopView`：价格牌正确（血药 30/祝福 40/普通卡 50/稀有卡 85/史诗卡 150），但"买卡"实际只转 XP（普通 10/稀有 25/史诗 50），**不发词典卡、无卡效果**；购买改的是内存 souls。
- 后端事实：无 `/shop/buy`、无抽卡记录、无卡持有持久化、souls 字段不存在（同专项 a）。
- 缺口归属：**前端负责 25 卡目录与 pity 计数/效果接线（边界内 Agent2 未做）；后端负责 souls 余额与商店购买/抽卡记录持久化（Agent3 未做）**。Agent2/Agent3 互相推责的两端均有实锤。
- 商店购买是否真实生效：**血药/祝福的本局效果生效**（加 HP/伤害倍率内存生效）；**词典卡购买完全不生效**（只发 XP）。

### d. DB 迁移（DB_VERSION 3→4，quick_pass 加列）—— ✅ 通过
- `ProgressStore.DB_VERSION = 4` ✓；`onUpgrade` oldV<4 分支 `ALTER TABLE progress ADD COLUMN quick_pass INTEGER DEFAULT 0`，try/catch(Throwable) 包裹 ✓；列已存在时静默忽略，幂等 ✓；纯 ADD COLUMN，不删表不丢数据 ✓。
- 附带核查：新建库 onCreate 已带 quick_pass 列，老库走 ALTER，两条路径都收敛 ✓。

### e. 硬约束复核
1. **全原生零 WebView 零网络**：✅ 运行时满足。全树 grep 无 `new WebView`/`addJavascriptInterface`/`new JsBridge`/`new LocalServer`/okhttp/HttpURL/youdao/dictvoice；发音走离线 `android.speech.tts.TextToSpeech`（BattleView:338-347）。⚠️ 残留：`bridge/JsBridge.java` 仍 `import android.webkit.WebView`，是**未被任何地方实例化的死代码**（见 GAP-12）。
2. **返回键=上一级**：✅ 主体满足（MainActivity.onBackPressed：BATTLE→battle.onBack() 弹"放弃本层战斗"二次确认；WRONGBOOK/SETTINGS/ACHIEVEMENTS→HOME；HOME→super 退出）。⚠️ 偏差见 GAP-10：抽卡/泉/商店/层间选择浮层打开时按返回仍弹"放弃战斗"，而非关浮层。
3. **拼写交互**：✅ 回车/EditorAction + 确认按钮提交；不自动反向（spell 的 correct 恒为单词）；删除键可用（原生 EditText backspace）；答错红字展示正确答案（0.6s 后变绿）——均在 BattleView 拼写分支核实。
4. **计时最后 3 秒小数**：✅ `secText`：s>3 显示整数、否则 `%.1f`。
5. **先见后拼（guardSpell）**：⚠️ **调用点未被本轮破坏，但存在自 V11.5 起的记录侧缺失**——全树 grep `exposed` 的写入点只有 `RunState.fromJson` 反序列化，**没有任何代码把词 put 进 exposed**（V11.6 基线 e2c2317 即如此，非本轮引入）。后果：guardSpell 永远判定"未见过"，spell 题被永久降级为 cn2en → **玩家在任何模式下都不会真正考拼写题**。详见 GAP-04。

### f. 交叉接口一致性
| 前端消费字段 | 后端实际 | 结论 |
|---|---|---|
| `new_ach`（answer 响应） | BackendRouter.answer 返回新成就数组 | ✅ 一致 |
| `wrong_by`（"level/qtype:n" 字符串数组） | markQuickWrong 写入 normal/hard/hell×题型；WrongbookView.tagOf 按 `[/:]` 切分渲染"普通判断×1" | ✅ 一致 |
| `quick_pass` | WordRec.quickPass + DB 第 4 列 + QuickEngine 压权重 | ✅ 一致 |
| `souls` | **后端无此字段** | ❌ 不一致（专项 a） |
| `streak` | 语义已改：仅远征通关 +1（markStreak 移入 /progress/finish） | ✅ 前后端口径一致（前端只展示） |
| `cards`（/progress/finish body） | 后端读取 `b.optInt("cards")` 解锁 cards5/10；**ResultView.onFinish 从不发送** | ❌ 不一致（GAP-05） |
| `enemy_confuse`（/draw body） | 后端读取 a.confuse；**BattleView.drawReq 从不发送** | ❌ 不一致（GAP-07） |
| enemy 信封 goal | 后端不下发；前端本地随机 | ❌ 不一致（专项 b） |

---

## 2. 逐条验收清单核对结果

图例：✅通过 / 🟡部分 / ❌未通过 / ➖不适用。

| 编号 | 结论 | 事实与差距 |
|---|---|---|
| 总则1 编译 | ✅ | assembleDebug BUILD SUCCESSFUL（exit 0，9s） |
| 总则2 零 WebView/网络 | ✅(+清理项) | 运行时零网络零 WebView；残留死代码 JsBridge.java 见 GAP-12 |
| 总则3 返回键=上一级 | 🟡 | 主链正确；浮层返回偏差 GAP-10 |
| 总则4 DB 升级不丢数据 | ✅ | 专项 d 通过 |
| ACC-01 词库 7388 | ✅ | assets/data/wordbooks.json meta=7388；字段 w/p/m/t/b/f；无 IPA |
| ACC-02 词书过滤/词数 | ✅ | 实算 高考3674/四级3846/六级5406/考研4801，精确命中；SettingsView 词书格显词数；draw 按 book 过滤 |
| ACC-03 题型阶梯 | ✅ | TYPE_LADDER/GRAD_REVIEW_TYPE/typeForWord(rec) 按 rec 状态出题 |
| ACC-04 死代码排除 | ✅ | POOL_* 常量已删；无 cn2cn 分支 |
| ACC-05 θ 自适应 | ✅ | updateTheta ±0.10/+0.05 钳 [1,3] 初始 1.5；adaptiveTier 分层；快刷不走 θ |
| ACC-06 SRS 双引擎 | ✅ | learnStep step±1、STEP_DELAY_MIN=[2,3,5]；gradStep SRS_LADDER 六档分钟数正确；stage→MASTERED |
| ACC-07 三档参数 | ✅ | quick levels 表、QUICK_CHOICE_T=8、QUICK_SPELL_TIME_ADD={0,1,3}、bossHp 30/45/60 均在 GameConfig/QuickEngine |
| ACC-08 词频权重 | ✅ | freqScore=max(0,1-log10(f)/log10(10000)) 实现 |
| ACC-09 quickWeight | ✅ | base×(1+errorBoost)；quick_pass 无错压到 passSuppress+0.03（QuickEngine:104） |
| ACC-10 drawQuickLocal | ✅ | spellC 候选→spellBias 加权抽拼→否则全池 typeForWord→不满足降级 cn2en；限时拼写 4+len×2+timeAdd、其余 8s |
| ACC-11 wrong_by 标签 | ✅ | 仅快刷记录档位×题型；markQuickPass 全程无错才置 true；WrongbookView 三档分组+标签渲染 |
| ACC-12 拼写资格 | ✅ | 长度>8 降级；未毕业需 step>=3（canSpellQuick） |
| ACC-13 干扰项构造 | 🟡 | 词书池排除本词/同组 ✓；judge 50% 别词释义 ✓；**confuse 敌人同组优先未生效**——前端从不发 enemy_confuse（GAP-07） |
| ACC-14 单局错词重现上限 | ✅ | runState.wrongSeen 封顶（cap() 控制不再抽）；无尽每深度重置 |
| ACC-15 新词节奏 | ✅ | NEW_CAP=20；tryNew=无到期或 rnd<0.30；找不到放宽 tier |
| ACC-16 伤害公式 | ✅(微注) | dmg=10×speed×combo×prof×card×revenge，ceil；comboMult=min(1+0.05c,2.0)；revenge=min(1.5+0.25(m-1),2)；拼写速度档见 GAP-11 待补核对 |
| ACC-17 连击/心流/凝神 | 🟡 | comboMult 公式 ✓；心流词魂连击≥5 发放 ✓；**凝神限时+2 秒只看到 feed 文案，未证实 timer 真加 2s**（GAP-09） |
| ACC-18 25 词典卡+pity | ❌ | 见专项 c：9 个通用奖励代替 25 卡；无 pity/epicPity；卡效果全缺；刷新不扣词魂 |
| ACC-19 词魂经济/商店 | 🟡 | 价格牌正确；souls 内存发/花；**无持久化**（专项 a）；祝福+25% 伤害本局生效 |
| ACC-20 XP 升级 | ✅ | 答对+2、新词+5；xpNeeded=120×level（ResultView/Stats 均读 xp_needed） |
| ACC-21 每日目标/打卡 | ✅ | 40 题/15 新学/1 远征；markStreak 仅远征通关+1；断签重计 |
| ACC-22 14 成就 | 🟡 | 14 个定义与解锁大部分接线；**master30/master60 无任何 unlock 调用**（GAP-06）；cards5/10 后端就绪但前端不发数（GAP-05） |
| ACC-23 持久化 key 映射 | 🟡 | SQLite 进度/meta/音频开关已持久；**词书 book、难度 difficulty、快刷档 quickLevel 每次冷启重置默认值**（MainActivity:41-43 无读回）（GAP-08） |
| ACC-24 视图状态机 | ✅ | Screen 枚举 9 视图本地切换，无 URL |
| ACC-25 返回键导航 | 🟡 | 主页/设置/错词本/战斗确认均对；**浮层（抽卡/泉/商店/层间选择）返回被"放弃战斗"对话框吞掉**（GAP-10） |
| ACC-26 主页布局 | ✅ | 横幅+🔥streak+XP 条；3 tab 金色高亮；快刷展开三格文案逐字命中 |
| ACC-27 设置页聚合 | 🟡 | 词书 4 格显词数/难度 5 格/声音 4 开关+2 滑条/数据/清空确认全部在；**ⓘ 用 Toast 长提示代替 tipbox 弹窗**（GAP-13） |
| ACC-28 战斗页布局 | ✅ | 顶栏层号/敌人卡 emoji+红血条/连击金字/题目卡词性 badge/阶梯/倒计时/玩家 HUD 齐全 |
| ACC-29 拼写抗卡顿 | ✅ | 可见 EditText 监听逐字母同步到格子；seq 一致；满格高亮；回车确认；答错红抖+正确答案变绿 |
| ACC-30 战斗动效 | 🟡 | 命中震动/飘字/红闪/低血 vignette 有；slash/hitring/burst 粒子样式为简化版（视觉等价，不计阻断） |
| ACC-31 翻牌演出 | ✅ | 3 卡 scaleX 翻牌；按稀有度从低到高 autoReveal；史诗金光+震动；刷新/放弃按钮在 |
| ACC-32 结算页 | ✅ | 大标题/等级 XP 条/2×2 统计/新词+错词 chips/再来一局；新学词数字见 GAP-14 |
| ACC-33 复习之泉 | ✅ | 先回 30 HP；本层错词 记得/不记得；答对+2 HP；**层错词未按 顽固>易错>生疏 排序**（按出错顺序） |
| ACC-34 词境商店 | ✅(经济侧缺口) | 五项价格牌与物品齐全；经济缺口并入 GAP-01/02 |
| ACC-35 错词本展示 | ✅ | 顽固/易错/生疏三组卡；词性 badge+错误次数+wrong_by 标签+顽固优先复习 |
| ACC-36 成就墙 | ✅ | 14 格半透明未解锁；new_ach 金色 toast 存在 |
| ACC-37 成瘾性演出 | 🟡 | 连击金字/心流横幅/凝神紫横幅/复仇徽章/LEVEL UP 均有；**敌人卡下微目标实时进度缺失**（依赖专项 b） |
| ACC-38 发音 | ✅ | 离线 TTS 美音，act.speak 开关联动，零网络 |
| ACC-39 选项控件 | ✅ | 4 选项带序号；judge ✓对/✗错大按钮；回车确认拼写（触屏无硬数字键，可接受） |

---

## 3. 差距明细（按优先级）

### P0
- **GAP-01（ACC-18）25 词典卡+pity 完全未落地**
  - 文件：`ui/RewardDrawView.java:135-165`（9 个通用奖励）、`ui/ShopView.java`（买卡=发 XP）
  - 差距：无 25 卡目录；无 pity>=3/epicPity>=6 保底；卡效果（灵光乍现+15% 暴击率、暴击风暴+25%、致命一击×3 倍、势如破竹连击减半）未接线；刷新一组不扣 25 词魂。
  - 修复：前端建 25 卡目录与 pity 计数、把卡效果作用到 DamageCalc 入参（critChance/critMult/comboMult 倍率）；后端补 souls 余额与抽卡/购卡记录接口（见 GAP-02）。

- **GAP-02（专项 a / ACC-19）souls 无后端持久化**
  - 文件：`store/ProgressStore.java`（无 souls 列/字段）、`server/BackendRouter.java`（无 /shop/buy）、`ui/BattleView.java:47`（MainActivity.souls 内存）
  - 修复：meta 加 souls 持久化；`POST /shop/buy` 校验价格扣 souls 返回余额；前端购买/刷新改走接口。

- **GAP-03（专项 b / ACC-28/37）enemy.goal 后端不下发**
  - 文件：`server/BackendRouter.java` enemySpawn；`ui/BattleView.java` pickGoal() 本地随机
  - 修复：enemy 信封加 goal 文案字段；前端改读并做进度计数。

- **GAP-04（专项 e）拼写题永不出现（先见后拼记录侧缺失）**
  - 文件：`core/RunState.java` exposed map 只反序列化无写入；`core/QuizEngine.java` guardSpell(141) 永远降级
  - 影响：spell 题型在 campaign/quick 均不会真考到（毕业靠 cn2en 顶过 step=3）。
  - 修复：draw 时对非 spell 题 `runState.exposed.put(word, true)` 并随 run_state 回传；或由前端出题成功后上报。此问题 V11.5 起即存在，本轮未引入但必须修。

- **GAP-05（ACC-22）cards 数不上报 → cards5/cards10 成就死锁**
  - 文件：`ui/ResultView.java:70-80` onFinish body 无 cards 字段；`server/BackendRouter.java:426` 等待 `b.optInt("cards")`
  - 修复：ResultView.onFinish 增加 `b.put("cards", act.runCards)`；BattleView 累计本层选卡数。

- **GAP-06（ACC-22）master30/master60 无解锁逻辑**
  - 文件：`server/BackendRouter.java` progressFinish 无 master 计数
  - 修复：finish 时统计 progress 中 MASTERED 数，>=30/60 unlock。

### P1
- **GAP-07（ACC-13）enemy_confuse 前端从不发送**
  - 文件：`ui/BattleView.java:463-470` drawReq 无 enemy_confuse
  - 修复：当 enemy.tpl=confuser 时 drawReq 加 `enemy_confuse=true`。

- **GAP-08（ACC-23）词书/难度/快刷档不持久**
  - 文件：`MainActivity.java:41-43` 冷启重置默认
  - 修复：启动从 SharedPreferences/wd_meta 读回 act.book/difficulty/quickLevel，设置页改动时写回。

- **GAP-09（ACC-17）凝神限时 +2 秒未证实生效**
  - 文件：`ui/BattleView.java` 连错 feed "凝神！限时+2s" 处
  - 修复：确认连错 2 次后下一题 timer 上限真 +2（timeLimit+2 传入）；若未加，补一行。

- **GAP-10（ACC-25）浮层返回被吞**
  - 文件：`ui/BattleView.java` onBack()
  - 修复：onBack 先判断当前是否有 choice/spring/shop overlay 可见，有则关浮层，无才弹放弃确认。

- **GAP-11（ACC-16/33）零散数值接线待补**
  - 拼写 speedMult 三档 [60%→2.0,40%→1.6,25%→1.25] 需对着 DamageCalc.speedMult spell 分支逐项核对；商店祝福+25% 伤害的下一层传递需确认跨层生效；SpringView 层错词排序改 顽固>易错>生疏。

### P2
- **GAP-12** `bridge/JsBridge.java` 仍 import android.webkit.WebView（死代码，无实例化）→ 删除该文件。
- **GAP-13** 设置页 ⓘ tipbox 为 Toast 长提示（ACC-27 期望 tipbox 弹窗）→ 可接受，下一轮补 popup。
- **GAP-14** 结算页"新学词"= questions-wrong 启发式，与下方 chips(runNewWords.size) 不一致 → 改用人参列表长度。

---

## 4. 回归风险轻查
- 远征战斗 draw/answer/finish 接线完整；DamageCalc 改 round→ceil、revenge 改单局 miss 为对齐 V7.0 的有意改动，非回归。
- 错词本 /wrongbook+/review 未动，标签解析一致。
- 成就 /stats/daily ach 展示未动；解锁收敛到 /progress/finish。
- 唯一行为变更提示：打卡 streak 从"每日首启动 +1"改为"打完一次远征才 +1"，符合 V7.0，主页/设置展示口径一致，无残留旧 ensureDaily 调用。
- 编译通过，无接线断裂。

## 5. Agent5 补充验收项（v1 遗漏）
- **ACC-40（Agent5 补充，P0）**：souls 钱包后端持久化 + 商店购买/抽卡刷新真实扣减（并入 GAP-01/02 一起验）。
- **ACC-41（Agent5 补充，P1）**：敌人卡下方微目标实时进度条/计数（后端下发 goal 后联动）。
- **ACC-42（Agent5 补充，P1）**：拼写题在可玩流程中实际出现（exposed 记录侧修复后，远征词能走到 spell 题型）。
- **ACC-43（Agent5 补充，P2）**：删除 bridge/JsBridge.java 死代码。
- **ACC-44（Agent5 补充，P2）**：词书/难度/快刷档冷启后保留用户上次选择。

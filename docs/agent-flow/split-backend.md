# split-backend.md —— V7.0 后端逻辑 / 数值拆分交付物

> **供谁使用**：Agent3（后端迁移）。
> **当前代码位置提示**：`android/app/src/main/java/com/guzifeng/worddungeon/core/`（QuizEngine/QuickEngine/SrsEngine/DamageCalc/EnemyModel/GameConfig/RunState/WordRec/WordBank/WordEntry/Quiz）+ `server/BackendRouter.java` + `store/ProgressStore.java`。
> **后端无截图资产，证据为代码引用**（函数名 / 行号，均对应单文件 `index.html`，共 3469 行；源码 `docs/v70-www/www/index.html`，git commit `5fe92b5`）。
> **内容来源**：逐条搬运自 `agent1-summary.md` 分区 A + 成瘾性设计数值侧，标注来源章节。UI/布局/动效/演出见 `split-frontend.md`。

---

## 0. 架构约束（来源：§0 总览）

- V7.0 是单文件 SPA：全部逻辑内联在 `index.html`，**没有独立路由**，所有「页面」都是同一 `<div id="app">` 内切换的视图（`G.screen` 状态机）。
- 没有构建步骤、没有外部 JS 依赖、没有外部音频文件（音效/BGM 全部 Web Audio 实时合成）。
- 纯前端可离线运行。

---

## A1. 单词逻辑（词库来源 / 词条字段 / 词性 / 释义）

### 词库来源与规模（来源：A1 词库来源与规模）
- **来源**：开源 **ECDICT** 词典，内嵌为 `const WORDS = [...]`（**行 928**，单行 58 万字符）。「关于」与 ⓘ 角标原文：「词库：开源 ECDICT 词典（7388 词，高考/四级/六级/考研）」（行 2635、2737）。
- **规模**：解析得 **7388 个词条**（去重后单词数）。
- **词条字段**（每词条 6 字段）：

  | 字段 | 含义 | 示例 |
  |---|---|---|
  | `w` | 英文单词 | `"abandon"` |
  | `p` | 词性（缩写带点） | `"v."`、`"n."`、`"adj."` |
  | `m` | 中文释义（逗号分隔多义） | `"放弃, 抛弃"` |
  | `t` | 难度档 tier 1–3 | `1` |
  | `b` | 所属词书数组（可多归属） | `["gaokao","cet4"]` |
  | `f` | 词频排名（整数 1 起；`999999` = 无频） | `1` |

- **没有音标/IPA 字段**。`p` 是词性不是音标；全文无任何音标数据。
- **词书归属数**（按 `b` 字段统计，一词可属多书）：`gaokao` 高考 **3674**、`cet4` 四级 **3846**、`cet6` 六级 **5406**、`kaoyan` 考研 **4801**。
- **tier 分布**：tier1 = 1595、tier2 = 1598、tier3 = 4195。
- **词频 `f` 范围**：1 ~ 999999；`999999` 表示「无频率数据」。
- **词性分布**：`n.` 4769、`adj.` 1230、`v.` 918、`adv.` 279、`pron.` 51、`prep.` 43、`num.` 38、空 34、`conj.` 16、`pl.` 7、`art.` 1、`int.` 1、`abbr.` 1。

### 词性处理（数据侧；badge 配色见 split-frontend.md §2）
- 释义直接取词条 `m` 字段整串展示，不做截断、不做选择拆分（行 2969 选择题选项、行 2897 judge、行 2969 cn2en 题面）。（来源：A1 释义处理方式）

### 易混词组（敌人机制用）（来源：A1 易混词组）
- `GROUPS`（行 930-936）：18 组易混/形近词对，如 `['adapt','adopt']`、`['reserve','preserve','conserve','deserve']`。
- `groupOf(w)`（行 938）：返回该词所在组，供「易混词妖」敌人优先从同组出干扰项（行 1824-1827）。

---

## A2. 出题逻辑（题型判定 / 拼写资格 / 干扰项 / 各题型规则）

### 题型由什么决定（来源：A2 题型由什么决定）
- **题型永远由该词的学习进度（阶梯 step/stage）决定，不是随机抽权重**。
- `typeForWord(rec)`（行 1155-1159）：已毕业（`graduated=true`）→ 取 `GRAD_REVIEW_TYPE[rec.stage]`；未毕业 → 取 `TYPE_LADDER[rec.step]`。
- 阶梯常量（行 1137-1139）：
  - `TYPE_LADDER = ['judge','en2cn','cn2en','spell']` —— 「判断 → 认义（英选中）→ 写义（中选英）→ 拼写」四级台阶。
  - `GRAD_REVIEW_TYPE = ['cn2en','spell','en2cn','cn2en','en2cn','spell']` —— 毕业后复习题型轮换。
- **⚠️ 死代码警告**：配置里的 `C.question.pool = {en2cn:0.55, cn2en:0.15, spell:0.10, judge:0.20}`（行 883）**全文无任何引用**，实际出题不按这个权重，迁移时**勿照抄**。

### 拼写资格判定（来源：A2 拼写资格判定）
- **远征/普通模式**：题目是 `spell` 且单词长度 > 8 时，降级为 `cn2en`（`buildQuestion` 行 1806、`pickWordAndType` 行 1606）。
- **快刷模式** `canSpellQuick(rec,w)`（行 1658-1663）：`w.length>8` → 不可拼；已毕业 → `typeForWord(rec)==='spell'` 才可拼；未毕业 → `rec.step>=3` 才可拼。

### 干扰项构造规则（来源：A2 干扰项构造规则）
- 主流程 `buildQuestion`（行 1818-1841）；快刷版 `drawQuickLocal`（行 1700-1721）。
- **en2cn（选中文释义）**：正确项 = 本词 `m`；从 `G.layerWords`（本层词书范围）排除本词、排除同组易混词后，洗牌取前 **3 个 `m` 不重复**的释义，与正确项混洗成 **4 个选项**。
- **cn2en（选英文单词）**：正确项 = 本词 `w`；同样从词书范围排除本词/同组，取前 **3 个不重复英文单词**，混洗成 **4 个选项**。
- **易混敌人特例**（`confuse`，行 1824-1827）：干扰项优先从同 `GROUPS` 组里取，制造形近干扰。
- **judge（判断对错）**（行 1842-1862）：初识词（无记录 或 `step===0` 未毕业）→ 固定展示正确释义，答案 `yes`；否则 50% 概率展示正确释义（`yes`），50% 展示别的词的释义（优先同组，否则词书随机），答案 `no`。选项恒为 `['yes','no']`。
- **spell（拼写）**：无选项，按字母格子输入。

### 判定标准（来源：A2 各题型规格表）
- `en2cn` / `cn2en` / `judge`：`choice===q.correct`（correct 分别 = 正确释义 / 正确单词 / yes-no）。
- `spell`：输入序列 `seq` 拼接 === `q.word`。
- **判定入口**：`answer(choice)`（行 1898-1902）→ `resolve(...)`（行 1903）。拼写超时自动结算：填满则按当前内容判对/错，未填满按超时（`startTimer` 行 1882-1889）。

---

## A3. 动态算法（词频权重 / 错误驱动 / 三档参数与公式）

> V7.0 核心。函数集中在行 1612-1723，配置在 `C.quick`（行 909-922）。**以下数值均为代码原文。**（来源：A3）

### 三档配置（`C.quick.levels`，行 910-913）

| 档 | id | 名称 | freqPow | spellBias | spellTimeAdd | baseFreq | enemyHp |
|---|---|---|---|---|---|---|---|
| 普通 | `normal` | 普通 | **1.0** | **0.0** | **0** | **1.0** | 30 |
| 困难 | `hard` | 困难 | **0.5** | **0.35** | **+1 秒** | **0.6** | 45 |
| 地狱 | `hell` | 地狱 | **0.2** | **0.75** | **+3 秒** | **0.3** | 60 |

### 其余 `C.quick` 全局参数（行 914-921）
- `levelFactor = {normal:1.0, hard:1.5, hell:2.0}`（错误加权的档系数）
- `qtypeFactor = {judge:1.0, en2cn:1.0, cn2en:1.3, spell:1.6}`（错误加权的题型系数）
- `errorCoeff = 1.0`
- `passSuppress = {hard:0.08, hell:0.05}`（无错且通过过的词，下次权重压到 base 的 8%/5%）
- `carousel = 0.03`（轮播保底权重）
- `freqRef = 10000`、`noFreqScore = 0.05`、`floor = 0.05`
- `choiceT = 8`（快刷选择题限时 8 秒）

### 词频权重 `freqScore(word)`（行 1614-1618）
```
f = 词条 f（无频则 999999）
if f >= 999999:  fs = noFreqScore = 0.05
else:            fs = max(0, 1 - log10(max(1,f)) / log10(10000))
```
含义：`f` 越小（越高频）→ `fs` 越接近 1；`f=10000` 时 `fs=0`。`freqPow` 越大高频词权重越集中；地狱档 `freqPow=0.2` 把高频优势压平。

### 综合权重 `quickWeight(word, rec, level)`（行 1664-1676）
```
fs   = freqScore(word)
base = floor(0.05) + baseFreq[level] * max(fs,0)^freqPow[level]
eb   = errorBoost(rec.wrong_by)          # 该词累计的「错误分」
w    = base * (1 + eb)
# 若该词无任何错误记录 且 rec.quick_pass===true：
w    = base * passSuppress[level] + carousel(0.03)   # 大幅压低（轮播）
return max(0, w)
```

### 错误驱动 `errorBoost(wb)`（行 1635-1643）
```
total = Σ over 档 g∈{normal,hard,hell} × 题型 t∈{judge,en2cn,cn2en,spell}:
        wb[g][t] * levelFactor[g] * qtypeFactor[t]
return total * errorCoeff(1.0)
```
`wb[g][t]` = 该词在「档 g、题型 t」下错过的次数（`wrong_by` 记录，见 A4）。错过越多、题型越重（spell 1.6 > cn2en 1.3 > judge/en2cn 1.0）、档越难（hell 1.5）→ 下次出题权重越高。

### 抽题流程 `drawQuickLocal(level)`（行 1677-1723）
1. 候选池 = `G.layerWords`（按当前词书过滤），且 `runCap`（本局错词重现 ≤1 次，见 A4）。
2. 先算拼写候选 `spellC`：池内通过 `canSpellQuick` 的词，按 `quickWeight` 加权、过滤 `>0`。
3. 若 `spellC` 非空 且 `rnd() < spellBias[level]` → 加权抽一个词出拼写题（地狱 spellBias 0.75 ≈ 75% 出题为拼写）。
4. 否则 → 全池按 `quickWeight` 加权抽一个词，题型取 `typeForWord(rec)`；落到 spell 但不满足 `canSpellQuick` 或长度 >8 → 降级 `cn2en`。
5. 限时：拼写 = `spell.base(4) + 长度*2 + spellTimeAdd[level]`；其余 = `choiceT(8)` 秒（行 1696-1698）。

### 普通/远征模式的自适应 θ（`updateTheta` / `adaptiveTier`）
- `updateTheta(theta, correct, fast)`（行 1140-1144）：答对且快 → `+0.10`；答对不快 → `+0.05`；答错 → `-0.10`；钳制 `[1,3]`。
- `adaptiveTier(theta)`（行 1145-1150）：`base = clamp(round(theta),1,3)`；`r<0.70` → `base` 档（match）；`r<0.85` → `base+1`（stretch）；其余 → `base-1`（consolidate）。
- θ 初始 1.5（`defaultMeta` 行 1104）。θ 仅在**普通/远征/无尽**模式决定抽词 tier（`pickWordAndType` 行 1549-1553）；**快刷模式不走 θ**，走词频+错误权重。
- 目标：抽词 tier 围绕 θ ±1 波动，把正确率稳在 ~85%（设置页 ⓘ 文案，行 2643）。

### 战斗伤害公式（`resolve` 行 1962-1986）
```
dmg = C.player.baseDamage(10)
      × speedMult       # 速度倍率
      × comboMult       # 连击倍率
      × profMult        # 熟练度倍率
      × cardMult        # 卡牌/祝福倍率
      × revengeMult     # 复仇倍率
crit → dmg × critMult
dmg = ceil(dmg)
```
- `speedMult`（`speedMultFor` 行 1536-1544）：拼写按剩余时间比例 `[60%→2.0, 40%→1.6, 25%→1.25, 其余→1.0]`；选择/判断按 `elapsed≤3s→1.5, ≤6s→1.2, 其余→1.0`。
- `comboMult = min(1 + 0.05×连击, 1+1.0)`（1.0 ~ 2.0，`C.combo` 行 882）。
- `profMult`（`C.proficiency` 行 884）：`NEW 1.0 / LEARNING 1.05 / FAMILIAR 1.2 / MASTERED 1.4`。
- `revengeMult`（行 1979-1982）：错过的词再次答对时 `min(1.5 + 0.25×(misses-1), 2.0)`。
- 暴击：默认无暴击率（`critChance=0`），靠卡牌加（如「灵光乍现」+15%）；默认暴击 ×2（`critMult=2`），史诗卡「致命一击」改 ×3。

---

## A4. 单词分配与出现方式（新词进入 / 错词重现 / 上限 / SRS）

### 记录结构（`P.words[word]`，`recOf` 行 1127-1129）
```
{ state: NEW|LEARNING|FAMILIAR|MASTERED,
  step: 0..3,          # 未毕业阶梯
  graduated: bool,     # 是否已毕业
  stage: 0..5,         # 毕业后 SRS 档位
  lapses: 0,           # 毕业后退学次数
  streak: 0,
  wrong_count: 0,
  due: 0,              # 下次到期时间戳
  created: 0,          # 今日新词计数用
  wrong_by: {normal/hard/hell × judge/en2cn/cn2en/spell: n}  # V7.0 错词本「在哪错过」
  quick_pass: bool }
```

### 新词如何进入出题流（`pickWordAndType` 行 1545-1608）
- 先算到期复习 `due = dueLearning + dueGraduated`（行 1202-1213）。
- `canNew = 今日新词数 < NEW_CAP(20)`（行 1136、1565）。
- `tryNew = canNew && (无到期词 或 rnd()<0.30)`（行 1577）——约 30% 概率穿插新词。
- 新词候选 = 本层词书里从未见过（`!P.words[w]`）且匹配目标 tier 的词；找不到则放宽 tier 限制（行 1580-1582、1594-1596）。

### 学习推进（`learnStep` 行 1160-1177）
- 未毕业时：答对 → `step+1`，`due = now + STEP_DELAY_MIN[step] 分钟`（`[2,3,5]`）；答错 → `step = max(0, step-1)`，`wrong_count++`。
- 走完 step=3（拼写）答对 → `graduated=true`、转 `FAMILIAR`、进入 SRS 毕业队列（行 1172-1175）。

### 毕业复习 SRS（`gradStep` 行 1178-1196）
- 间隔阶梯 `SRS_LADDER = [10,1440,4320,10080,23040,43200]` 分钟（行 1135）= **10分钟 / 1天 / 3天 / 7天 / 16天 / 30天**。
- 答对快（fast）→ `stage+1`；答对不快 → 需 `streak>=2` 才 `stage+1`。
- 答错（毕业后退学）→ `stage=0, graduated=false, step = wrong_count>=2?1:2`，回到学习态。
- `stage` 到顶（5）→ `state=MASTERED`。
- ⚠️ `C.famIntervals=[2,4,7,15]`（行 890）**是死代码**，实际用 `SRS_LADDER`。

### 复习/错词如何重现
- 到期词进入 `dueLearning/dueGraduated` 队列，战斗中优先被抽（行 1562-1564、1584-1586）。
- **单局重现上限**（行 1561、1680、1794、2037-2038）：一个词在一次远征/快刷里**首次答错后，只再重现 1 次**（`runWrongSeen>=1` 后本局不再抽），之后仅归档。
- 无尽模式每个深度重置 `runWrong/runWrongSeen`（`enterLayer` 行 1423）。

### 快刷的错词记录（V7.0 新增）
- `markQuickWrong(rec, level, qtype)`（行 1644-1650）：`rec.wrong_by[level][qtype]++`，并 `quick_pass=false`。
- `markQuickPass(rec)`（行 1651-1657）：若全程无任何错误记录，则 `quick_pass=true`（用于 passSuppress 轮播）。
- 错词本「在哪错过」标签生成：`wrongByTags(w)`（行 2842-2856），如「地狱拼写×2」「困难判断×1」。

---

## A5. 状态 / 存储 / 路由（横切）

### 全局可变状态（来源：A5）
- `G`（`newRun` 行 1289-1301）：单局状态。关键字段：`screen/mode/layer/node/hp/shield/gold/combo/flow/wrongStreak/focus/cards/vengeance/battle/choice/stats`。
- `P`（`Save.load` 行 1126）：持久化学习进度 = `{words:{}, meta:{level,xp,theta,streak,lastActive,endlessBest,daily,ach}}`。
- `SETTINGS`：词书/难度（行 1084）。`AUD`：音频开关（行 668）。

### localStorage 键映射（全部可离线持久化；迁移目标 = ProgressStore / Android 存储）

| Key | 内容 | 出处 |
|---|---|---|
| `word_dungeon_progress_v1` | 学习进度 `P`（词记录/meta/成就/打卡） | 行 1100 |
| `word_dungeon_book_v1` | 词书 + 难度选择 | 行 1070 |
| `word_dungeon_audio_v1` | 音效/BGM/震动/发音开关与音量 | 行 667 |
| `wd_quick_level` | 快刷档位（normal/hard/hell） | 行 1247 |
| `wd_api_base` / `wd_api_token` | 可选后端地址与游客 token | 行 1253、1256 |

### 视图路由内容（`render` 行 3157-3177 的 switch）
- `G.screen` 取值：`home / battle / choice / spring / shop / result / map / wrongbook / settings / achievements / stats / howto`。
- 没有 URL hash、没有浏览器历史；切换靠改 `G.screen` 再 `render()` 重绘。（各视图 UI 布局见 split-frontend.md。）

### 可选后端 API 层（`API`，行 1252-1286）
- 默认 `API.base` 为空 → 纯离线，完全不联网（行 1257）。
- 配置后：出题 `/quiz/draw`、答题 `/quiz/answer`、到期 `/review/due`、错词本 `/wrongbook`、统计 `/stats/daily`。连不上自动 `API.on=false` 降级本地（`apiDrawAndBuild` catch，行 1813-1816）。
- **迁移提示**：Android 版若纯离线，可整段忽略 `API` 对象，本地逻辑已自洽。

---

## B7-数值. 成瘾性设计 · 数值侧（来源：B7）

### 连击与心流 / 凝神
- 连击加成数值：`comboMult = min(1+0.05×连击, 2.0)`；答对 `combo++`，答错清零（有卡「势如破竹」则降半）。
- **心流**：连击 ≥ `flow.threshold(5)` 进入，之后每答对额外 +1 词魂（`flow.bonusGold=1`）。
- **凝神**：连续答错 `focus.wrongStreak(2)` 次 → 下一题限时 +2 秒（`focus.extraTime`）。

### 暴击 / 复仇数值
- 暴击靠卡牌：「灵光乍现」+15% 暴击率、「暴击风暴」+25%、「致命一击」暴击 ×3（默认 `critChance=0`、`critMult=2`）。
- 复仇倍率：错过的词再次答对时 `min(1.5 + 0.25×(misses-1), 2.0)`（演出见 split-frontend.md §6.2）。

### 战斗微目标（`BATTLE_GOALS`，行 1490-1494）
每场战斗随机抽一个：连击达 4（奖 6 HP）/ 答对 5 题（奖 6 词魂）/ 无错击败敌人（奖 10 词魂）。

### 每日目标 + 打卡
- 每日三目标（`C.daily` 行 896）：今日答对 40 题 / 新学 15 词 / 完成 1 次远征。
- 远征完成才记打卡 `streak`（`markStreak` 行 1322-1335）；连续 streak+1，断签重计。

### 成就（14 个，`ACHIEVEMENTS` 行 1035-1050）
- Boss 类：初战告捷（层1 Boss）/ 穿透迷雾（层2）/ 征服深渊（层3）/ 远征通关。
- 战斗类：连击高手（单局连击 10）/ 弹无虚发（单局答对 50）。
- 收集类：初具规模（一局 5 卡）/ 词典大成（一局 10 卡）。
- 学习类：渐入佳境（累计掌握 30）/ 词汇达人（累计掌握 60）。
- 打卡类：坚持不懈（3 天）/ 一周不辍（7 天）。
- 无尽类：深渊行者（抵达 5 层）/ 深渊主宰（抵达 10 层）。

### 升级与奖励节奏
- XP：答对 +2，新词额外 +5（`C.xp`）；升级所需 `xpNeeded = 120 × level`（行 1340）。
- 经济循环：战斗赢词魂（普通 15 / 精英 35 / Boss 60，冠军 Boss ×1.5）→ 商店买卡/血药 → 更强卡 → 打更深层。
- 商店价格（`startShop` 行 2467）：血药 30 / 临时祝福 40（下一层伤害+25%）/ 普通卡 50 / 稀有卡 85 / 史诗卡 150。
- **抽卡保底**（`drawChoice` 行 2351）：`pity>=3` 保底出稀有、`epicPity>=6` 保底出史诗；每局胜利限免费刷新 1 次（25 词魂）。
- 词典卡共 **25 张**、三级稀有度（普通/稀有/史诗）。

---

## 附录 A：V6.0 / V6.1 演进背景（迁移上下文）（来源：附录 A）

来自 `git log 5fe92b5`：
- **V4.0** 前后端 + Capacitor Android + 错词分级；**V4.1** ECDICT 7388 词内嵌；**V4.2** θ 自适应 + SM-2；**V4.3** 题型阶梯；**V4.4** 单局错词重现 2 次封顶；**V4.5** 无尽模式；**V4.7** 主页重做；**V4.8** 音频合成系统 / 词典卡 25 张 / 前后端接入层（可连后端，离线兜底）。
- **V5.1 Android 返回键**：`handleAndroidBack` + 战斗二次确认（行 3431-3460）——前端侧，见 split-frontend.md §4.6。
- **V5.2 卡死修复**：拼写输入改「隐藏 input + seq 权威序列」，抗输入法/联想/自动纠错导致的卡顿乱序（行 3256-3316）——前端侧，见 split-frontend.md §4.3。
- **V6.0 完全离线**：7388 词全部内嵌 `WORDS`，默认 `API.base` 为空、完全不联网也能跑（行 1257、2717 ⓘ 文案「默认完全离线」）。
- **V6.1 美式发音**：新增 `WordSpeaker`，有道 dictvoice `type=2` 美音，联网自动读、离线不发请求、异步非阻塞（行 840-869）——前端音频侧，见 split-frontend.md §3。
- **V7.0（本次）**：新增快刷三档 + 词频/错误驱动动态出题 + 错词本「在哪错过」标签。

## 附录 B：死代码 / 存疑清单（迁移时可忽略或清理）（来源：附录 B）

| 项 | 位置 | 说明 |
|---|---|---|
| `C.question.pool` | 行 883 | 题型权重池，**从未被引用**；实际题型走 `TYPE_LADDER` |
| `C.famIntervals=[2,4,7,15]` | 行 890 | 注释称 FAMILIAR 复习间隔，**从未被引用**；实际用 `SRS_LADDER` |
| `C.xp.master=10` | 行 895 | 掌握词 XP，**从未被引用** |
| `cn2cn` 分支 | 行 1822 | 条件里列了 `cn2cn`，但无任何地方产出该题型，死分支 |
| 成就注释「12 项」 | 行 1033 | 实际 `ACHIEVEMENTS` 有 **14 项** |
| 词库注释「60 个四级核心词，分 3 档」 | 行 926 | 实际已扩到 **7388 词、4 词书**，注释过时 |

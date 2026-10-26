# 单词地下城 V7.0（HTML 版）设计总结 —— Agent1 事实基线

> 本文档是 5-Agent 流水线的**唯一事实基线**。后续 Agent 全部以本文件为准，不得另起炉灶。
> 所有结论均抄录自源码，标注「函数名 / 行号」。行号对应单文件 `index.html`（共 3469 行）。
> **源码**：`docs/v70-www/www/index.html`（git commit `5fe92b5`）
> **范围**：以 V7.0 主体为准；V6.0/V6.1 仅作演进背景（见文末附录 A）。

---

## 0. 总览与阅读说明

- 这是一个**单文件 SPA**：全部 HTML/CSS/JS 内联在 `index.html`，**没有独立路由**，所有「页面」都是同一 `<div id="app">` 内切换的视图（`G.screen` 状态机）。
- 没有构建步骤、没有外部 JS 依赖、没有外部音频文件（音效/BGM 全部 Web Audio 实时合成）。
- 纯前端可离线运行；可选连接一个自建 FastAPI 后端（`API` 层，见 §2.6），连不上自动降级本地逻辑。
- 配色：深蓝主题 `--bg0:#0d1828` 等（`:root`，行 11-16）；`#app` 最大宽度 520px、手机视口全宽（行 24-26）。

---

# 分区 A：后端逻辑 / 数值（对应任务 1/2/3/4 + 状态/存储/路由）

## A1. 单词逻辑（词库来源 / 词条字段 / 词性 / 释义）

### 词库来源与规模
- **来源**：开源 **ECDICT** 词典，内嵌为 `const WORDS = [...]`（**行 928**，单行 58 万字符）。设置页「关于」与 ⓘ 角标原文：「词库：开源 ECDICT 词典（7388 词，高考/四级/六级/考研）」（行 2635、2737）。
- **规模**：解析得 **7388 个词条**（去重后单词数）。
- **词条字段**（每个词条 6 个字段，无遗漏）：
  | 字段 | 含义 | 示例 |
  |---|---|---|
  | `w` | 英文单词 | `"abandon"` |
  | `p` | **词性**（缩写带点） | `"v."`、`"n."`、`"adj."` |
  | `m` | **中文释义**（逗号分隔多义） | `"放弃, 抛弃"` |
  | `t` | **难度档 tier 1–3** | `1` |
  | `b` | **所属词书数组**（可多归属） | `["gaokao","cet4"]` |
  | `f` | **词频排名**（整数 1 起；`999999` = 无频） | `1` |
- **没有音标/IPA 字段**。`p` 是词性不是音标；全文无任何音标数据。
- **词书归属数**（按 `b` 字段统计，一词可属多书）：
  - `gaokao` 高考：**3674**
  - `cet4` 四级：**3846**
  - `cet6` 六级：**5406**
  - `kaoyan` 考研：**4801**
- **tier 分布**：tier1 = 1595、tier2 = 1598、tier3 = 4195（tier3 明显多，因低频难词多）。
- **词频 `f` 范围**：1 ~ 999999；`999999` 表示「无频率数据」。
- **词性分布**：`n.` 4769、`adj.` 1230、`v.` 918、`adv.` 279、`pron.` 51、`prep.` 43、`num.` 38、空 34、`conj.` 16、`pl.` 7、`art.` 1、`int.` 1、`abbr.` 1。

### 词性处理
- 显示时用 `posBadge(p)`（行 1406-1412）：把 `p` 按 `/` 切分，每个词性生成一个带色 badge。
- 配色映射 `POS_COLOR`（行 1405）：`n→cyan`、`v→orange`、`adj→green`、`adv→teal`、`prep→purple`、`conj/pron→mut`，未匹配一律 `--mut`。
- 用途：战斗题目标注词性（`qhead` 里 `posBadge`，行 2970）、错词本列表（行 2874）、复习之泉（行 3071）。

### 释义处理方式
- 直接取词条 `m` 字段整串展示，不做截断、不做选择拆分（行 2969 选择题选项、行 2897 judge、行 2969 cn2en 题面）。
- 四选一/判断题的「错误释义」来自**别的词的 `m` 字段**（见 A2 干扰项规则）。

### 易混词组（敌人机制用）
- `GROUPS`（行 930-936）：18 组易混/形近词对，如 `['adapt','adopt']`、`['reserve','preserve','conserve','deserve']`。
- `groupOf(w)`（行 938）：返回该词所在组，供「易混词妖」敌人优先从同组出干扰项（行 1824-1827）。

---

## A2. 出题逻辑（题型判定 / 拼写资格 / 干扰项 / 各题型规则）

### 题型由什么决定
- **题型永远由该词的学习进度（阶梯 step/stage）决定，不是随机抽权重**。
- `typeForWord(rec)`（行 1155-1159）：
  - 已毕业（`graduated=true`）→ 取 `GRAD_REVIEW_TYPE[rec.stage]`
  - 未毕业 → 取 `TYPE_LADDER[rec.step]`
- 阶梯常量（行 1137-1139）：
  - `TYPE_LADDER = ['judge','en2cn','cn2en','spell']` —— 即「判断 → 认义（英选中）→ 写义（中选英）→ 拼写」四级台阶。
  - `GRAD_REVIEW_TYPE = ['cn2en','spell','en2cn','cn2en','en2cn','spell']` —— 毕业后复习题型轮换。
- **关键结论**：配置里的 `C.question.pool = {en2cn:0.55, cn2en:0.15, spell:0.10, judge:0.20}`（行 883）**是死代码，全文无任何引用**。实际出题不按这个权重。

### 拼写资格判定
- **远征/普通模式**：题目是 `spell` 且单词长度 > 8 时，降级为 `cn2en`（`buildQuestion` 行 1806、`pickWordAndType` 行 1606）。
- **快刷模式**用 `canSpellQuick(rec,w)`（行 1658-1663）：
  - `w.length>8` → 不可拼；
  - 已毕业 → `typeForWord(rec)==='spell'` 才可拼；
  - 未毕业 → `rec.step>=3`（即走完阶梯前三步）才可拼。

### 干扰项构造规则（`buildQuestion` 行 1818-1841；快刷版 `drawQuickLocal` 行 1700-1721）
- **en2cn（选中文释义）**：正确项 = 本词 `m`；从 `G.layerWords`（本层词书范围）里排除本词、排除同组易混词后，洗牌取前 **3 个 `m` 不重复**的释义，与正确项混洗成 **4 个选项**。
- **cn2en（选英文单词）**：正确项 = 本词 `w`；同样从词书范围排除本词/同组，取前 **3 个不重复英文单词**，混洗成 **4 个选项**。
- **易混敌人特例**（`confuse`，行 1824-1827）：干扰项优先从同 `GROUPS` 组里取，制造形近干扰。
- **judge（判断对错）**（行 1842-1862）：
  - 初识词（无记录 或 `step===0` 未毕业）→ **固定展示正确释义**，答案 `yes`；
  - 否则 50% 概率展示正确释义（`yes`），50% 展示**别的词的释义**（优先同组，否则词书随机），答案 `no`。
  - 选项恒为 `['yes','no']` 两个按钮。
- **spell（拼写）**：无选项，按字母格子输入（见 B6 交互）。

### 各题型的具体规格（按钮文案 / 判定标准）
| 题型 | 题面（行 2965-2973） | 选项/控件 | 按钮文案 | 判定 |
|---|---|---|---|---|
| `en2cn` | 显示英文单词 + 词性 badge，提示「选择正确的中文释义」 | 4 个释义按钮，带序号 1-4（行 2926-2927） | 直接显示释义文本 | `choice===q.correct`（correct=正确释义）|
| `cn2en` | 显示中文释义 + 词性 badge，提示「选择正确的英文单词」 | 4 个英文按钮，带序号 1-4 | 直接显示单词文本 | `choice===q.correct`（correct=正确单词）|
| `judge` | 「判断下方释义」+ 词性，下方显示一个释义，提示「这个释义对吗？」 | 2 个大按钮（行 2898-2901） | ✓「对」 / ✗「错」 | `choice===q.correct`（yes/no）|
| `spell` | 显示中文释义 + 词性，提示「根据释义默写英文单词」 | 隐藏 input + 字母格子（行 2886-2893） | 首字母提示 + 格子 | 输入序列 `seq` 拼接 === `q.word` |

- **键盘**：战斗中按数字 1–4 直接选前 4 个选项（行 3395-3403）；拼写题回车确认（行 3276-3279）。
- **判定入口**：`answer(choice)`（行 1898-1902）→ `resolve(...)`（行 1903）。拼写超时自动结算逻辑见 `startTimer`（行 1882-1889）：填满则按当前内容判对/错，未填满按超时。

---

## A3. 动态算法（词频权重 / 错误驱动 / 三档参数与公式）

> 这是 V7.0 的核心。函数集中在行 1612-1723，配置在 `C.quick`（行 909-922）。**以下数值均为代码原文。**

### 三档配置（`C.quick.levels`，行 910-913）
| 档 | id | 名称 | freqPow | spellBias | spellTimeAdd | baseFreq | enemyHp |
|---|---|---|---|---|---|---|---|
| 普通 | `normal` | 普通 | **1.0** | **0.0** | **0** | **1.0** | 30 |
| 困难 | `hard` | 困难 | **0.5** | **0.35** | **+1 秒** | **0.6** | 45 |
| 地狱 | `hell` | 地狱 | **0.2** | **0.75** | **+3 秒** | **0.3** | 60 |

- 其余 `C.quick` 全局参数（行 914-921）：
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
- 含义：`f` 越小（越高频）→ `fs` 越接近 1；`f=10000` 时 `fs=0`。`freqPow` 越大（普通档 1.0）高频词权重越集中；地狱档 `freqPow=0.2` 把高频优势压平，变得「不挑高频、更平均」。

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
- `wb[g][t]` = 这个词在「档 g、题型 t」下错过的次数（`wrong_by` 记录，见 A4）。
- 含义：错过越多、错过的题型越「重」（spell 系数 1.6 > cn2en 1.3 > judge/en2cn 1.0）、错过的档越难（hell 1.5）→ 该词下次出题权重越高。

### 抽题流程 `drawQuickLocal(level)`（行 1677-1723）
1. 候选池 = `G.layerWords`（按当前词书过滤），且 `runCap`（本局错词重现 ≤1 次，见 A4）。
2. 先算**拼写候选** `spellC`：池内通过 `canSpellQuick` 的词，按 `quickWeight` 加权、过滤 `>0`。
3. 若 `spellC` 非空 且 `rnd() < spellBias[level]` → **加权抽一个词出拼写题**（地狱 spellBias 0.75 ≈ 75% 出题为拼写，即「地狱拼写为主」）。
4. 否则 → 全池按 `quickWeight` 加权抽一个词，题型取 `typeForWord(rec)`；若落到 spell 但不满足 `canSpellQuick` 或长度 >8 → 降级 `cn2en`。
5. 限时：拼写 = `spell.base(4) + 长度*2 + spellTimeAdd[level]`；其余 = `choiceT(8)` 秒（行 1696-1698）。

### 普通/远征模式的自适应 θ（`updateTheta` / `adaptiveTier`）
- `updateTheta(theta, correct, fast)`（行 1140-1144）：
  - 答对且快（fast）→ `+0.10`；答对不快 → `+0.05`；答错 → `-0.10`；钳制 `[1,3]`。
- `adaptiveTier(theta)`（行 1145-1150）：
  - `base = clamp(round(theta),1,3)`
  - `r<0.70` → 出 `base` 档（match）；`r<0.85` → `base+1`（stretch，拉伸）；其余 → `base-1`（consolidate，巩固）。
- θ 仅在**普通/远征/无尽**模式决定抽词 tier（`pickWordAndType` 行 1549-1553）；**快刷模式不走 θ**，走上面的词频+错误权重。

### 战斗伤害公式（`resolve` 行 1962-1986）
```
dmg = C.player.baseDamage(10)
      × speedMult       # 速度倍率
      × comboMult       # 连击倍率
      × profMult        # 熟练度倍率
      × cardMult        # 卡牌/祝福倍率
      × revengeMult      # 复仇倍率
crit → dmg × critMult
dmg = ceil(dmg)
```
- `speedMult`（`speedMultFor` 行 1536-1544）：拼写按剩余时间比例 `[60%→2.0, 40%→1.6, 25%→1.25, 其余→1.0]`；选择/判断按 `elapsed≤3s→1.5, ≤6s→1.2, 其余→1.0`。
- `comboMult = min(1 + 0.05×连击, 1+1.0)`（即 1.0 ~ 2.0，`C.combo` 行 882）。
- `profMult`（`C.proficiency` 行 884）：`NEW 1.0 / LEARNING 1.05 / FAMILIAR 1.2 / MASTERED 1.4`。
- `revengeMult`（行 1979-1982）：错过的词再次答对时 `min(1.5 + 0.25×(misses-1), 2.0)`。
- 暴击：默认无暴击率（`critChance=0`），靠卡牌加（如「灵光乍现」+15%）；默认暴击 ×2（`critMult=2`），史诗卡「致命一击」改 ×3。

---

## A4. 单词分配与出现方式（新词进入 / 错词重现 / 上限 / SRS）

### 记录结构（`P.words[word]`，`recOf` 行 1127-1129）
```
{ state: NEW|LEARNING|FAMILIAR|MASTERED,
  step: 0..3,          # 未毕业阶梯
  graduated: bool,      # 是否已毕业
  stage: 0..5,          # 毕业后 SRS 档位
  lapses: 0,            # 毕业后退学次数
  streak: 0,
  wrong_count: 0,
  due: 0,               # 下次到期时间戳
  created: 0,          # 今日新词计数用
  wrong_by: {normal/hard/hell × judge/en2cn/cn2en/spell: n}  # V7.0 错词本「在哪错过」
  quick_pass: bool }
```

### 新词如何进入出题流（`pickWordAndType` 行 1545-1608）
- 先算到期复习 `due = dueLearning + dueGraduated`（行 1202-1213）。
- `canNew = 今日新词数 < NEW_CAP(20)`（行 1136、1565）。
- `tryNew = canNew && (无到期词 或 rnd()<0.30)`（行 1577）——即有约 30% 概率穿插新词。
- 新词候选 = 本层词书里**从未见过**（`!P.words[w]`）且匹配目标 tier 的词；找不到则放宽 tier 限制（行 1580-1582、1594-1596）。

### 学习推进（`learnStep` 行 1160-1177）
- 未毕业时：答对 → `step+1`（从 judge 爬到 spell），`due = now + STEP_DELAY_MIN[step] 分钟`（`[2,3,5]`）；答错 → `step = max(0, step-1)`，`wrong_count++`。
- 走完 step=3（拼写）答对 → `graduated=true`、转 `FAMILIAR`、进入 SRS 毕业队列（行 1172-1175）。

### 毕业复习 SRS（`gradStep` 行 1178-1196）
- 间隔阶梯 `SRS_LADDER = [10,1440,4320,10080,23040,43200]` 分钟（行 1135）= **10分钟 / 1天 / 3天 / 7天 / 16天 / 30天**。
- 答对快（fast）→ `stage+1`；答对不快 → 需 `streak>=2` 才 `stage+1`。
- 答错（毕业后退学）→ `stage=0, graduated=false, step = wrong_count>=2?1:2`，回到学习态。
- `stage` 到顶（5）→ `state=MASTERED`。
- ⚠️ `C.famIntervals=[2,4,7,15]`（行 890）**是死代码**，实际用的是 `SRS_LADDER`。

### 复习/错词如何重现
- 到期词进入 `dueLearning/dueGraduated` 队列，战斗中优先被抽（行 1562-1564、1584-1586）。
- **单局重现上限**（行 1561、1680、1794、2037-2038）：一个词在一次远征/快刷里**首次答错后，只再重现 1 次**（`runWrongSeen>=1` 后本局不再抽），之后仅归档。
- 无尽模式每个深度重置 `runWrong/runWrongSeen`（`enterLayer` 行 1423）。

### 快刷的错词记录（V7.0 新增）
- `markQuickWrong(rec, level, qtype)`（行 1644-1650）：在 `rec.wrong_by[level][qtype]++`，并 `quick_pass=false`。
- `markQuickPass(rec)`（行 1651-1657）：若全程无任何错误记录，则 `quick_pass=true`（用于 passSuppress 轮播）。
- 错词本展示「在哪错过」标签：`wrongByTags(w)`（行 2842-2856），如「地狱拼写×2」「困难判断×1」。

### θ 自适应机制（小结）
- θ 初始 1.5（`defaultMeta` 行 1104），范围 1–3，答对快+0.1 / 答对+0.05 / 答错-0.1（行 1140-1144）。
- 作用：抽词 tier 围绕 θ ±1 波动，目标把正确率稳在 ~85%（设置页 ⓘ 文案，行 2643）。

---

## A5. 状态 / 存储 / 路由（横切）

### 全局可变状态
- `G`（`newRun` 行 1289-1301）：单局状态。关键字段：`screen/mode/layer/node/hp/shield/gold/combo/flow/wrongStreak/focus/cards/vengeance/battle/choice/stats`。
- `P`（`Save.load` 行 1126）：持久化的学习进度 = `{words:{}, meta:{level,xp,theta,streak,lastActive,endlessBest,daily,ach}}`。
- `SETTINGS`：词书/难度（行 1084）。`AUD`：音频开关（行 668）。

### localStorage 键（全部可离线持久化）
| Key | 内容 | 出处 |
|---|---|---|
| `word_dungeon_progress_v1` | 学习进度 `P`（词记录/meta/成就/打卡） | 行 1100 |
| `word_dungeon_book_v1` | 词书 + 难度选择 | 行 1070 |
| `word_dungeon_audio_v1` | 音效/BGM/震动/发音开关与音量 | 行 667 |
| `wd_quick_level` | 快刷档位（normal/hard/hell） | 行 1247 |
| `wd_api_base` / `wd_api_token` | 可选后端地址与游客 token | 行 1253、1256 |

### 视图路由（`render` 行 3157-3177 的 switch）
- `G.screen` 取值：`home / battle / choice / spring / shop / result / map / wrongbook / settings / achievements / stats / howto`。
- 没有 URL hash、没有浏览器历史；切换全靠改 `G.screen` 再 `render()` 重绘 `#app.innerHTML`（行 3173）。

### Android 返回键（`handleAndroidBack` 行 3432-3460）
- 仅在 Capacitor 环境注册（`initAndroidBack` 行 3452-3460）。
- 设置/错词本/成就/统计/怎么玩/结算 → 回主页；战斗 → 弹「放弃本层战斗？」确认框（`.backconfirm`，行 652-661）；抽卡/泉/商店 → 回战斗；主页 → `Capacitor.Plugins.App.exitApp()`。

### 可选后端 API 层（`API`，行 1252-1286）
- 默认 `API.base` 为空 → 纯离线，完全不联网（行 1257）。
- 配置后：出题 `/quiz/draw`、答题 `/quiz/answer`、到期 `/review/due`、错词本 `/wrongbook`、统计 `/stats/daily`。**连不上自动 `API.on=false` 降级本地**（`apiDrawAndBuild` catch，行 1813-1816）。
- **迁移提示**：Android 版若纯离线，可整段忽略 `API` 对象，本地逻辑已自洽。

---

# 分区 B：前端 UI / 交互（对应任务 5/6/7）

## B5. 功能清单（逐项实际行为）

| 功能 | 实际行为 | 出处/截图 |
|---|---|---|
| **主页** | 顶部横幅（等级徽章+标题+🔥连续打卡天数+XP 条）；中部发射卡（模式 tab + 开始按钮）；底部 dock 三入口（错词本/成就/设置） | `viewHome` 行 2513；截图 `01-home.png` |
| **三种模式** | 远征（3 层闯关）/ 无尽（无上限下潜）/ 快刷（高频词速刷）。tab 在主页切换（`homeMode`），选中态金色高亮 | 行 2563-2576；`01-home.png` |
| **快刷三档** | 主页选「快刷」后展开 普通/困难/地狱 三格按钮；文案分别为「高频优先·识别快刷 / 错词加权·进阶 / 拼写为主·拼写压轴」 | 行 2578-2585；`05-home-quick.png` |
| **词书选择** | 4 选 1：高考/四级/六级/考研，每格显示词数；切换即时过滤出题范围 | `viewSettings` 行 2636-2642；`02-settings.png` |
| **难度选择** | 5 格：自适应/全部/简单/中等/困难；「自适应」按 θ 抽词，选具体档则锁定 tier | 行 2644-2650 |
| **战斗** | 敌人卡（血条/受击演出）+ 连击 + 题目卡（题型/词性/阶梯/倒计时）+ 玩家 HUD（血/盾/词魂） | `viewBattle` 行 2918；`07-battle-campaign.png` |
| **抽卡（词典卡）** | 战斗胜利后翻 3 张 3D 卡（普通/稀有/史诗），可「刷新一组·25 词魂」或「放弃」；自动从低稀有到高稀有依次翻 | `viewChoice` 行 2993；`08-choice-flip.png` |
| **复习之泉** | 每层固定节点：先回 30 HP，再把本层错词（顽固>易错>生疏排序）做成「记得/不记得」复习，答对每词 +2 HP | `startSpring` 行 2430；`10-spring.png` |
| **词境商店** | 每层固定节点：血药 30 / 临时祝福 40（下一层伤害+25%）/ 普通卡 50 / 稀有卡 85 / 史诗卡 150，用「词魂」买 | `startShop` 行 2467；`11-shop.png` |
| **错词本** | 独立视图：按错误次数分 顽固(≥3)/易错(=2)/生疏(=1) 三组列出，显示单词+词性+释义+错误次数+「在哪错过」标签；可「开始复习（顽固优先）」 | `viewWrongBook` 行 2857；`03-wrongbook.png` |
| **成就墙** | 14 个成就（见 B7），主页 dock 进入，未解锁半透明 | `viewAchievements` 行 2747；`04-achievements.png` |
| **每日目标** | 今日答对 40 题 / 新学 15 词 / 完成 1 次远征；主页与设置页显示进度 | `C.daily` 行 896；`01/02` |
| **打卡 streak** | 完成一次远征才打卡（`markStreak`）；连续则 streak+1，断签重计；主页横幅显示「🔥 N 天」 | 行 1322-1335 |
| **发音** | 战斗进入新单词卡自动读**美式发音**；走有道 `dict.youdao.com/dictvoice?audio=W&type=2` MP3；需联网，离线不发音不发请求 | `WordSpeaker` 行 845-869 |
| **声音/触感设置** | 音效/BGM/震动/发音 4 个开关 + 音效/BGM 两条音量滑条；全部 Web Audio 合成，无音频文件 | `viewSettings` 行 2657-2685 |
| **数据清空** | 设置页底部「清空所有学习进度」（confirm 后 `Save.reset()`） | 行 2743、3389 |

---

## B6. 页面设计与交互设计（布局/控件/动效/反馈/导航/返回）

### 主页（`viewHome`）
- 布局：`home-banner`（等级圆徽 + 标题「单词地下城」+「词灵使 Lv.x · 应试单词战斗」+ 右上 🔥streak）→ 渐变 XP 条 → `launch-card`（3 模式 tab → 开始大按钮）→ `home-dock` 三宫格。
- 「开始」按钮为金色渐变（`.launch-btn`），下方小字显示模式描述。

### 设置页（`viewSettings`，单页聚合）
- 顶部 `subhead`：‹ 返回 + 标题「设置」+ 副标题「V7.0 · 快速刷题版」。
- 分组卡片：词书选择 / 难度选择 / 声音与触感 / 数据（等级/打卡/θ/今日目标/到期复习）/ 错词本入口 / 成就墙缩略 / 云端后端（可选）/ 怎么玩 / 关于。
- 每个分组标题旁有 ⓘ 角标（`.info-tip`），点击弹小窗 `#tipbox` 解释（`showTip` 行 3418）。

### 战斗页（`viewBattle`）—— 最复杂的视图
- 自上而下：顶栏（第 N 层词境 + 节点圆点进度）→ 心流/凝神横幅 → **敌人卡**（`.enemy`，脸 emoji「魔/精/魇」+ 名字 + 「答错/超时将受 X 点伤害」+ 红血条 + 本场微目标）→ **连击行**（金色大数字）→ **题目卡**（题面 + 词性 badge + 复仇徽章 + 题型阶梯 `识/认/写/拼` + 倒计时条 + 选项/拼写格）→ **玩家 HUD**（绿血条 + 盾 + 词魂）。
- **拼写输入**（`spellCells` 行 2886，`afterRender` 行 3249-3318）：隐藏 `<input>` + 一排字母格子；点格子/点提示自动聚焦；按键逐字母入格（`seq` 权威序列，抗输入法乱序/联想/纠错）；满格金色高亮，回车确认或等限时自动结算；答错先红抖展示错误拼写、0.6s 后换成正确答案变绿（`markOptions` 行 2077-2089）。
- **动效**：攻击斩击波 `.slash`、命中环 `.hitring`、粒子 `burst`、敌人受击 `enemyhit/enemycrit`、伤害飘字 `floatText`、屏幕红闪 `flashred`、低血量红 vignette（`.vignette.lowhp`）。

### 翻牌抽卡（`viewChoice`）
- 3 张 `.flip-card` 3D 翻转；自动按稀有度从低到高依次翻（`autoReveal` 行 3051），史诗压轴放金光粒子；底部「刷新一组 / 放弃」。

### 结算页（`viewResult`）
- 大标题（远征通关/失败/无尽抵达第 N 层）→ 等级卡（XP 条）→ 2×2 统计格（答题数/正确率/最高连击/新学词）→ 新学词 & 错词 chip 列表 → 再来一局按钮。截图 `09-result.png`。

### 导航与返回
- 视图间切换靠 `data-act` 事件委托（`afterRender` 行 3349-3391）：`homeback` 回主页、`quickexit` 退快刷、`springdone/shopdone` 推进节点、`wrongback` 回主页。
- Android 返回键逻辑见 A5（含战斗中二次确认防误触）。

---

## B7. 成瘾性设计（连击/暴击/复仇/目标/成就/打卡/升级/奖励节奏）

### 连击与心流
- 答对 `combo++`，答错清零（有卡「势如破竹」则降半）；连击加成 `min(1+0.05×连击, 2.0)`。
- **心流**：连击 ≥ `flow.threshold(5)` 进入（`G.flow=true`），之后每答对额外 +1 词魂（`flow.bonusGold=1`），全屏青色光晕 `flowglow` + 「心 流」横幅。
- **凝神**：连续答错 `focus.wrongStreak(2)` 次 → 下一题限时 +2 秒（`focus.extraTime`），紫色「凝神」横幅。

### 暴击 / 复仇
- 暴击靠卡牌（灵光乍现 15%、暴击风暴 +25%、致命一击 ×3），暴击橙色大字 + 斩击加重。
- **复仇徽章**：本局错过的词再次出现时，题目旁显示「⚔ 再战 · 伤害X%」金色脉冲徽章（行 2971）；答对触发「复仇 +N」红字 + 「雪耻！」飘字（行 2186-2187）。

### 战斗微目标（`BATTLE_GOALS`，行 1490-1494）
- 每场战斗随机抽一个：连击达 4（奖 6 HP）/ 答对 5 题（奖 6 词魂）/ 无错击败敌人（奖 10 词魂）。敌人卡下方实时显示进度。

### 每日目标 + 打卡
- 每日三目标（40 题/15 新词/1 远征），主页可见进度；远征完成才记打卡 `streak`，连续天数给「坚持不懈 3 天 / 一周不辍 7 天」成就。

### 成就（14 个，`ACHIEVEMENTS` 行 1035-1050）
- Boss 类：初战告捷（层1 Boss）/ 穿透迷雾（层2）/ 征服深渊（层3）/ 远征通关。
- 战斗类：连击高手（单局连击 10）/ 弹无虚发（单局答对 50）。
- 收集类：初具规模（一局 5 卡）/ 词典大成（一局 10 卡）。
- 学习类：渐入佳境（累计掌握 30）/ 词汇达人（累计掌握 60）。
- 打卡类：坚持不懈（3 天）/ 一周不辍（7 天）。
- 无尽类：深渊行者（抵达 5 层）/ 深渊主宰（抵达 10 层）。
- 解锁时顶部弹金色 toast「成就解锁」（`showAchToast` 行 1391）。

### 升级与奖励节奏
- XP：答对 +2，新词额外 +5（`C.xp`）；升级所需 `xpNeeded = 120 × level`（行 1340）。
- 升级触发全屏金色放射演出「LEVEL UP · 词灵使 Lv.X」+ 粒子（`showLevelUp` 行 1354）。
- 经济循环：战斗赢词魂（普通 15/精英 35/Boss 60，冠军 Boss ×1.5）→ 商店买卡/血药 → 更强卡 → 打更深层。
- **抽卡保底**（`drawChoice` 行 2351）：`pity>=3` 保底出稀有、`epicPity>=6` 保底出史诗；每局胜利限免费刷新 1 次（25 词魂）。

---

# 附录 A：V6.0 / V6.1 演进背景（git 祖先链）

来自 `git log 5fe92b5`：
- **V4.0** 前后端 + Capacitor Android + 错词分级；**V4.1** ECDICT 7388 词内嵌；**V4.2** θ 自适应 + SM-2；**V4.3** 题型阶梯；**V4.4** 单局错词重现 2 次封顶；**V4.5** 无尽模式；**V4.7** 主页重做；**V4.8** 音频合成系统 / 词典卡 25 张 / 前后端接入层（可连后端，离线兜底）。
- **V5.1 Android 返回键**：`handleAndroidBack` + 战斗二次确认（行 3431-3460）。
- **V5.2 卡死修复**：拼写输入改「隐藏 input + seq 权威序列」，抗输入法/联想/自动纠错导致的卡顿乱序（行 3256-3316）。
- **V6.0 完全离线**：7388 词全部内嵌 `WORDS`，默认 `API.base` 为空、完全不联网也能跑（行 1257、2717 ⓘ 文案「默认完全离线」）。
- **V6.1 美式发音**：新增 `WordSpeaker`，有道 dictvoice `type=2` 美音，联网自动读、离线不发请求、异步非阻塞（行 840-869）。
- **V7.0（本次）**：新增快刷三档 + 词频/错误驱动动态出题 + 错词本「在哪错过」标签。

---

# 附录 B：死代码 / 存疑清单（迁移时可忽略或清理）

| 项 | 位置 | 说明 |
|---|---|---|
| `C.question.pool` | 行 883 | 题型权重池，**从未被引用**；实际题型走 `TYPE_LADDER` |
| `C.famIntervals=[2,4,7,15]` | 行 890 | 注释称 FAMILIAR 复习间隔，**从未被引用**；实际用 `SRS_LADDER` |
| `C.xp.master=10` | 行 895 | 掌握词 XP，**从未被引用** |
| `cn2cn` 分支 | 行 1822 | 条件里列了 `cn2cn`，但无任何地方产出该题型，死分支 |
| 成就注释「12 项」 | 行 1033 | 实际 `ACHIEVEMENTS` 有 **14 项** |
| 词库注释「60 个四级核心词，分 3 档」 | 行 926 | 实际已扩到 **7388 词、4 词书**，注释过时 |

---

# 附录 C：截图清单（真机 Chromium 渲染，viewport 390×844）

均存于 `docs/agent-flow/screenshots/`：

| 文件 | 视图 | 说明 |
|---|---|---|
| `01-home.png` | 主页（远征默认） | 横幅/模式tab/开始/dock |
| `02-settings.png` | 设置页 | 词书/难度/声音/数据/成就缩略 |
| `03-wrongbook.png` | 错词本 | 分组列表 |
| `04-achievements.png` | 成就墙 | 14 格 |
| `05-home-quick.png` | 主页-快刷tab | 三档选择 |
| `06-battle-quick.png` | 快刷战斗 | 退出口/已刷计数/judge题 |
| `07-battle-campaign.png` | 远征战斗 | 敌人卡/微目标/阶梯/倒计时 |
| `08-choice-flip.png` | 翻牌抽卡 | 三稀有度卡 |
| `09-result.png` | 结算 | 统计格/词chip |
| `10-spring.png` | 复习之泉 | 记得/不记得 |
| `11-shop.png` | 词境商店 | 血药/祝福/卡牌 |

> 截图通过 `playwright` 打开本地 http.server 真机渲染获得，非 HTML 结构提取。已结束 http.server 进程。

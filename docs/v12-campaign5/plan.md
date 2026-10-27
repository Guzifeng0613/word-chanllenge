# V12.0 远征 3 层扩 5 层 · 三轮监督迭代计划（监督制）

- 仓库：`/home/user/Doubao/chats/38448800358997506/word-dungeon/`
- 分支：Test（已 `git merge main` 同步至 V11.9 / versionCode 26）
- 基线：远征原 3 层（1 普通 / 2 精英 / 3 Boss），存在「闯关太简单、一层一个敌人就过」问题。
- 目标：远征扩为 5 层，每层难度阶梯递增；本轮只做设计落地 + 构建 + 三轮静态审查修复 + 提交推送 Test，**不提 PR、不合 main、不做最终交付上传**。

## 一、五层节点节奏（已冻结数值，直接落地）

| 层 | 层名 | 词档 TIER | 小怪数 MINIONS | 层末守门者 CLIMBER | 选择题限时 CHOICE_TIME | 拼写权重 SPELL_BIAS | 拼写加时 SPELL_TIME_ADD |
|---|---|---|---|---|---|---|---|
| 1 | 热身回廊 | 1 | 2 | none | 8s | 0.00 | 0 |
| 2 | 精英哨站 | 2 | 2 | elite | 7s | 0.10 | 0 |
| 3 | 词难断层 | 3 | 3 | none | 6s | 0.25 | 0 |
| 4 | 精英殿卫 | 3 | 2 | elite | 5s | 0.40 | 1 |
| 5 | 深渊王座 | 3 | 1 | boss（overlord） | 5s | 0.60 | 2 |

每层 = 若干普通小怪 + 可选层末守门者；小怪清完才到守门者，守门者击杀才算过层；层内小怪之间不开复习之泉/商店，过层后才走「精英末场翻牌 → 复习之泉 → 商店 → 下一层」。

## 二、敌人数值（EnemyModel，hp/atk 扩为 5 元素，按下标 layer-1）

| 模板 | hp(L1..L5) | atk(L1..L5) |
|---|---|---|
| wordfiend 词魔 | 32/44/70/76/90 | 7/9/11/13/15 |
| confuser 易混词妖 | 36/48/74/80/94 | 8/10/12/14/16 |
| speller 拼写守卫 | 40/52/78/84/98 | 9/11/13/15/17 |
| ghost 健忘幽灵(精英) | 90/108/130/158/180 | 11/13/15/17/19 |
| polysem 多义词魔(精英) | 100/118/140/168/190 | 12/14/16/18/20 |
| overlord 词魇·深渊领主(Boss) | 320（固定） | 18（固定，prefer spell，timeMod 2） |

曲线复核（每层普通小怪均值 HP → 层总血量含守门者）：
- 均值 HP：36 / 48 / 74 / 80 / 94（单调递增）
- 层总血量：72 / 209 / 222 / 323 / 414（单调递增）；第 5 层 overlord 单场约需 ~19 题，显著难于旧 3 层 Boss(~15 题)。

## 三、改动落点

- `core/GameConfig.java`：`LAYER_COUNT=5` + 7 个 CAMPAIGN_* 权威数组。
- `core/EnemyModel.java`：普通/精英 hp/atk 扩 5；新增 overlord；campaign boss 分支改层 5 固定 overlord，下标 clamp 防越界。
- `server/BackendRouter.java`：`/enemy/spawn` 新增 `enc`，按 MINIONS/CLIMBER 裁决普通/守门者，下发 `enc_index/layer_enc_total/is_layer_final/layer_name`。
- `core/QuizEngine.java`：campaign 词档下限 CAMPAIGN_TIER（手动/自适应取 max）；按层 SPELL_BIAS 升级拼写（超长词不拼写、仍走 guardSpell）；非拼写限时 CHOICE_TIME、拼写加时 SPELL_TIME_ADD；无尽不变。
- `ui/BattleView.java`：enc 字段、spawn 带 enc、onKill 分支（非末场连打下一场、第 5 层 Boss 末场通关、否则层间链）、shouldDrawReward 改读敌人 elite、进度圆点 5、层名播报、Boss 红光仪式。
- `ui/AmbientBackground.java`：新增 setLayer，两团辉光按层 青→青蓝→金→橙→红（未主题化保持原始青+金，无尽不回归）。
- 版本：`build.gradle` versionName 12.0 / versionCode 27；CHANGELOG、GDD 头部更新。

## 四、每轮验收维度（静态审查清单）

1. **曲线单调**：HP/atk/词档/限时/拼写权重逐层单调（限时 8→5 递减即难度递增，权重递增）。
2. **数组边界**：普通/精英数组 5 元素下标合法；campaign boss 不再 `boss.get(4)`；所有层号 clamp。
3. **通关判断**：仅第 5 层 Boss 末场（is_layer_final && layer>=5）才 finishRun(true)。
4. **先见后拼**：guardSpell 在拼写升级之后仍生效。
5. **无尽无回归**：endless 的 spawn/draw/限时/辉光逻辑不变。
6. **构建**：`./gradlew clean assembleDebug` 通过；aapt 校验 versionCode=27/versionName=12.0。

## 五、三轮节奏

每轮：构建 → 对照清单静态审查 → 记录真实问题并修复 → 复核通过后落 iteration-<n>.md。三轮问题须真实、有区分度。

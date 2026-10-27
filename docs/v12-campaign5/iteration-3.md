# V12.0 监督迭代 · R3（终轮）

## 构建结果
- 命令：`./gradlew clean assembleDebug`（全量 clean）
- 结果：**BUILD SUCCESSFUL**。
- aapt 校验：`versionCode='27' versionName='12.0'` ✅。

## 静态审查发现的问题
1. **[关于卡版本/模式文案残留] SettingsView 关于卡仍写死旧版本与旧层数**
   - 副标题 `V11.9 · 快速刷题版`；
   - 关于正文 `模式：远征 3 层闯关 …`、`版本：V11.9 · 全原生快速刷题（7388 词已内置）`。
   - 现象：与 R2 主页/结算同类问题——用户可见的关于页仍宣传旧版本号与「3 层」，与五层实装不一致。

## 修复
- `SettingsView.java`：副标题 V11.9→V12.0；关于正文模式行 `远征 3 层闯关`→`远征 5 层远征`、版本行 V11.9→V12.0。

## 终轮复核（逐项过清单）
- 曲线单调：普通小怪均值 HP 36/48/74/80/94 ↑；层总血量 72/209/222/323/414 ↑；词档 1/2/3/3/3 不减；限时 8/7/6/5/5 不增；拼写权重 0/.1/.25/.4/.6 ↑ ✅
- 数组边界：普通/精英 hp/atk 均 5 元素、下标 clamp；campaign Boss 层 5 固定 overlord，不再 `boss.get(4)`；BackendRouter/QuizEngine 层号均 clamp ✅
- 通关判断：仅 `is_layer_final && layer>=LAYER_COUNT(5)` 才 finishRun(true)；其余末场走层间链 ✅
- 先见后拼：拼写升级后仍走 guardSpell，未见过词降级 cn2en 并标记 exposed ✅
- 无尽无回归：endless 的 spawn/draw/限时路径未改；氛围背景未主题化时保持原始青+金 ✅
- 构建：clean assembleDebug 通过；aapt 27/12.0 ✅

## 结论
三轮（辉光回归 / 主页结算层数文案 / 设置关于卡版本文案）问题真实且有区分度，均已修复并复绿。R3 通过，五层设计落地完成。

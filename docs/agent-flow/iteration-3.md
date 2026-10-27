# 迭代 3：错词本体验 + 成就反馈 + 设置诚实化与稳定性兜底（V11.9 R3）

- 基线：b57b5ab（R2）→ 提交 e55a726（短哈希），改动文件仅 6 个：ui/BattleView.java、ui/ResultView.java、ui/HomeView.java、ui/WrongbookView.java、ui/SettingsView.java、MainActivity.java（+166/−20）
- 闭环：验收标准定稿 → 诊断差距（稳定性与错词本审计 + 组织者裁决）→ 实现（agent R3实现）→ 独立编译 → 独立复核（agent R3复核）
- 状态：**通过（7/7）**，进入终期回归与交付

## 验收清单与判定

| # | 验收项（代码级） | 判定 | 证据锚点 |
|---|---|---|---|
| 1a | 答题成就 toast 中文映射（ACH_NAMES 14 key 与后端 unlock 全集对齐，未知 key 原样返回） | ✅ | MainActivity:152-181；BattleView:1053 |
| 1b | 结算页成就反馈：/progress/finish 快照 diff + new_ach 优先，主线程中文 Toast + Sfx.EPIC，opt+try/catch | ✅ | ResultView:79-82/94-95/113-123；局内成就已入基线不重复弹 |
| 2 | 每日目标上主页：三档进度 + 完成度封顶 100%，daily==null GONE，optInt 防御，后端零改动 | ✅ | HomeView:101-105/390-408 |
| 3a | 错词复习批次内存缓存：startReview 一次性载入、renderReview 不查库、refresh 清缓存、队列顺序对齐 | ✅ | WrongbookView:25/59/252-261/289 |
| 3b | 结算页"去错词本复习"按钮（仅有错词时显示，act.show(WRONGBOOK) 直达） | ✅ | ResultView:200-208 |
| 3c | 错词按 wrong_count 降序（渲染与复习队列共用排序） | ✅ | WrongbookView:73-86 |
| 4a/b/c | 设置页：BGM 假开关+滑条已删（音效/震动/发音保留）；版本号→V11.9；speak 冷启回读同键同默认 | ✅ | SettingsView:186-193/:93；MainActivity:143-146 |
| 5a | onDetachedFromWindow 统一清理（BattleView 自身 main Handler + fillAnim.cancel），与 act.main() 结算链两实例辨析不冲突 | ✅ | BattleView:1732/1734 |
| 5b | 结算线程与主页 refreshMeta 解析兜底（双层 try/catch + opt） | ✅ | ResultView:78；HomeView refreshMeta |
| 6 | 词魂语义：主页/结算无可见词魂展示（HomeView souls 仅内存同步）；未调 addSouls、未改 DB_VERSION、未持久化（裁决：V11.8 局内制为刻意设计，不逆转） | ✅ | HomeView:388；DB_VERSION=5 未变 |
| 7 | 编译 assembleDebug BUILD SUCCESSFUL（独立 --rerun-tasks 全量 108 tasks 重跑） | ✅ | 复核实测 |

边界核查：diff 仅 6 个允许文件；versionName/Ui 色板/server/store/core/docs 未变；无新增音频枚举；R1 连击心流与 R2 流畅度行为未破坏。

## 真机行为（预期描述，沙箱无真机，待用户上机确认）

答题/结算解锁成就时弹出中文提示 + 琶音（不再出现英文 key）；主页首屏可见"今日目标"进度行；错词复习翻卡流畅无卡顿、结算页一键直达错词本、错词按错次降序；设置页不再有无效的 BGM 开关，发音开关冷启行为与显示一致；快速退出战斗无残留动画回调。

## 剩余差距 / 观察项（部分纳入终期交付微修）

1. （已纳入终期微修）快照 diff 在 fbefore==null 时会把全量 ach 误判为新成就——终期交付加"fbefore==null 跳过"加固。
2. （已纳入终期微修）combo10 toast 文案"连击 10！"与成就墙「连击高手」不一致——终期交付按成就墙名称对齐。
3. （已纳入终期微修）设置页 About 卡正文（SettingsView:334）仍写 V11.0——随最终升版统一为 V11.9。
4. 遗留 BGM 旧偏好键（bgm/vol_bgm）无读取方，残留无害，未清理存储。
5. 真机视觉走查未做（沙箱限制）。

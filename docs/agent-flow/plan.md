# V11.9 三轮迭代优化计划（监督制）

- 仓库：/home/user/Doubao/chats/38448800358997506/word-dungeon/
- 基线：main = be872d1（V11.8，versionCode 25，versionName "11.8"），工作区干净
- 执行方式：组织者制定验收标准并监督，实现/验证由子 agent 执行，组织者不直接改代码
- 文档：本计划 + 每轮 iteration-<n>.md + 终期 iteration-final.md（均落 docs/agent-flow/）

## 一、三轮迭代总表

| 轮次 | 主题 | 迭代目标（真机可感知） | 验收要点（候选，诊断后定稿） |
|---|---|---|---|
| R1 | 连击心流闭环 + 打击反馈 | 连击心流从"5格点亮"升级为完整闭环：答错清槽有反馈、满5触发有全局联动、里程碑有文案 | 代码级：updateComboDots 答错红闪→塌缩清空；满5全槽联动闪光+心流横幅同帧；comboSub 里程碑文案；编译：assembleDebug 通过；真机：连答5题闪光横幅、答错瞬间槽红闪、无卡顿 |
| R2 | 倒计时与战斗节奏流畅度 | 消除倒计时 100ms 台阶感；怪物血条/切题过渡/击杀衔接平滑化 | 代码级：计时 UI 改连续插值渲染（保留 0.1s 结算精度）；受击/击杀血条衔接；切题过渡差异化；编译通过；真机：倒计时无台阶、血条平滑、切题无生硬跳变 |
| R3 | 错词本体验 + 稳定性兜底 | 错词本排序/筛选/复习入口；成就/每日目标补缺；后端响应解析与数据层异常兜底 | 代码级：错词本交互补全；审计风险点逐一兜底；不改 DB_VERSION（如需必带 v5→v6 迁移）；编译通过；真机：错词本操作流畅、异常数据不闪退 |

## 二、每轮验收标准框架（三条并行）

1. **代码级**：写明改动文件:方法，行为可读码验证（如"答错分支调用 clearComboAnimation()"）。
2. **编译级**：`cd android && export JAVA_HOME=<仓库>/.build/jdk-17.0.13+11 && export ANDROID_HOME=<仓库>/.build/android-sdk && export PATH=$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH && ./gradlew assembleDebug` 必须通过，零新增警告级错误。
3. **真机行为描述**：沙箱无真机，验收标准中"真机行为"为用户应在设备上观察到的预期行为描述；沙箱内以代码实现 + 编译通过为验证依据，并在 iteration 文档中诚实标注"真机行为为预期描述，待用户上机确认"。

## 三、闭环执行流程（每轮）

验收标准定稿 → 诊断差距（已由只读诊断 agent 完成）→ 派实现子 agent 修改 → 编译验证（实现 agent 自证 + 组织者抽查日志）→ 独立复核子 agent 对照验收清单逐项勾选 → 通过则落盘 iteration-<n>.md 并进入下一轮；未通过则发回原实现 agent 修复，验收不过不进入下一轮。

## 四、硬约束（全程不可越界）

- 色系 #0d1828 深蓝体系不变（不改 Ui.java 配色常量）
- 零 WebView、零网络依赖、后端本地离线不上云
- 返回键 = 上一级（不改变 MainActivity 路由语义）
- 不重做 V11.7/V11.8 已交付内容（投射弹道/Sfx 音效/词魂局内制/连击槽前置钩子等）
- ProgressStore DB_VERSION v5：默认不新增持久化字段；如必须则带 v5→v6 迁移逻辑
- 改动必须真实解决可感知问题，不堆砌装饰；小改动原则，每项改动附风险说明

## 五、版本与交付策略

- 每轮实现后本地提交（版本号保持 11.8/25 中间态，不打包）
- 三轮全部通过后最终提交统一升版：versionName 11.9 / versionCode 26（小改动 +0.1 原则）
- 交付链路：assembleRelease + assembleDebug → 复制为仓库根固定名 单词地下城-release-10.0.apk / -debug-10.0.apk → FileBatchUpload → present_files → CHANGELOG.md 补 V11.9 条目 → git commit + push origin main（push 偶发断连则重试）
- 签名 keystore .build/worddungeon.keystore（密码 WordDungeon2026），密文绝不上库

## 六、终期交付

- 全量回归：编译（release+debug）+ 关键路径代码走查（主页→选词书难度→闯关战斗→答对答错→错词本→成就→设置）
- 输出 docs/agent-flow/iteration-final.md（三轮验收清单汇总、每轮通过率、最终剩余差距、回归结果）
- 向 MainAgent 返回汇总：三轮验收标准摘要、每轮通过率、最终剩余差距、APK 交付

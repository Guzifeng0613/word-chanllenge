# V12.0 监督迭代 · R1

## 构建结果
- 命令：`cd android && export JAVA_HOME=<repo>/.build/jdk-17.0.13+11 && export ANDROID_HOME=<repo>/.build/android-sdk && ./gradlew clean assembleDebug`
- 结果：**BUILD SUCCESSFUL**（clean 全量，1m35s）。
- aapt 校验：`versionCode='27' versionName='12.0'` ✅。

## 静态审查发现的问题
1. **[视觉回归] AmbientBackground 无尽/默认辉光第二团由「金」变「青 teal」**
   - 现象：`glowLayer` 默认 1，`GLOW_B[0]=0x2DD4BF(teal)`；无尽模式从不调用 `setLayer`，于是无尽背景第二团辉光从原始金色 `0xFBBF24` 变成青色，属于本任务明确禁止的「无关改动/无尽回归」。
   - 影响：无尽深渊氛围与 V11.9 不一致。

## 修复
- `ui/AmbientBackground.java`：新增 `boolean themed=false`；未主题化（无尽/默认）时 shader 仍用原始 青(0x38BDF8)+金(0xFBBF24)；`setLayer(layer)` 置 `themed=true` 后才按层取 GLOW_A/B 色板。
- 复核：campaign 每层 startRun/advanceLayer 都会 `setLayer(layer)`，层色偏移生效；无尽保持原色。

## 复核结论
- 曲线单调 ✅；数组下标全部 clamp ✅；通关仅 layer5 Boss 末场 ✅；guardSpell 在拼写升级后仍生效 ✅；无尽辉光/出题路径恢复不变 ✅。
- 修复后增量构建 **BUILD SUCCESSFUL**。R1 通过，进入 R2。

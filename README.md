# 单词地下城 Word Dungeon

应试单词 · 战斗 Roguelike。认出单词就是攻击，背错一次就会挨打；错词按错误次数分级，顽固词反复出现直到掌握。

## 玩法

- 答对单词攻击敌人，答得越快伤害越高，连击还会再加成；连击 ≥5 进入心流。
- 四种题型：看英文选中文释义（55%）、看中文选英文（15%）、拼写（10%）、快速判断（20%）。
- 题目标注词性（n./v./adj.…），同形近形词辨析。
- 答错 / 超时敌人反击、连击清零，错词进错词本；错过的词再答对触发复仇（1.5 起，上限 2.0）。
- 错词分级：L3 顽固（≥3 错，权重 ×3）/ L2 易错（2 错，×2）/ L1 生疏（1 错，×1），战斗内加权反复出现，复习按顽固→易错→生疏。

## 工程结构

```
word-dungeon/
├── word-dungeon-demo.html      # 单文件可玩原型（离线，浏览器直接打开）
├── GDD-单词地下城.md           # 游戏设计文档（本地真值）
├── gdd-feishu.xml              # 飞书文档 XML
├── design-contract.json        # 设计契约（规则/参数/测试，可校验）
├── server/                     # 后端（主要代码）
│   ├── main.py                 # FastAPI：词库、组卷、结算、错词本、同步、统计
│   ├── core.py                 # 核心逻辑：四题型、干扰项、分级、间隔重复、伤害
│   └── data/                   # 词库 words.json（开源词库映射）+ 易混组
├── www/                        # 客户端静态资源（Capacitor webDir）
├── android/                    # Capacitor 生成的 Android 工程
├── capacitor.config.json
└── 单词地下城-debug.apk         # 已构建的 Android 安装包
```

## 运行后端

```bash
cd server
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
# 接口文档：http://localhost:8000/docs
```

主要接口：`POST /auth/guest`、`GET /bank/tier`、`POST /quiz/draw`、`POST /quiz/answer`、`GET /wrongbook`、`POST /wrongbook/review`、`POST /progress/sync`、`GET /stats/daily`。

## 重新打包 APK

> 需 JDK 17 与 Android SDK（platform-34、build-tools 34.0.0）。

```bash
# 更新客户端资源后
npx cap sync android
cd android
./gradlew assembleDebug
# 产物：android/app/build/outputs/apk/debug/app-debug.apk
```

## 词库（V4 方向）

开源词库 COCA 2 万高频 + 四六级大纲 + CEFR 分级；每词主释义取常用义（前 2 高频义位），干扰项从同词性、近形 / 近义族生成。当前原型为 61 词示例词库（tier 1/2/3）。

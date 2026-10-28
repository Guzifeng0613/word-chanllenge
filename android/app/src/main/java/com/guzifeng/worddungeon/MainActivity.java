package com.guzifeng.worddungeon;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.guzifeng.worddungeon.server.BackendRouter;
import com.guzifeng.worddungeon.ui.AchievementsView;
import com.guzifeng.worddungeon.ui.AltarView;
import com.guzifeng.worddungeon.ui.BattleView;
import com.guzifeng.worddungeon.ui.HomeView;
import com.guzifeng.worddungeon.ui.TopToast;
import com.guzifeng.worddungeon.ui.ResultView;
import com.guzifeng.worddungeon.ui.SettingsView;
import com.guzifeng.worddungeon.ui.StatsView;
import com.guzifeng.worddungeon.ui.Ui;
import com.guzifeng.worddungeon.ui.WrongbookView;

import org.json.JSONObject;

/**
 * 全原生版主入口（V10.0）：
 * 彻底移除 WebView / JS 桥 / Capacitor / HTTP 通道，
 * UI 全部为 Android 原生 View，直接内存调用 BackendRouter（复用全部后端引擎与 SQLite）。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WordDungeon";
    public static MainActivity instance;

    private FrameLayout root;
    private BackendRouter router;
    private volatile boolean backendReady = false;
    private String backendErr = null;
    private final Handler main = new Handler(Looper.getMainLooper());

    // 全局游戏状态（跨页面）
    public String mode = "campaign";        // campaign / endless / quick
    public String book = "gaokao";          // 词书 id 或 "all"
    public String difficulty = "adaptive";  // adaptive / easy / normal / hard / hell
    public String quickLevel = "normal";    // 快刷三档 normal / hard / hell
    public int lastBest = 0;                // 无尽最高纪录（主页展示）
    public boolean speak = true;
    public JSONObject bankMeta = null;
    // V11.7 对齐 V7.0：词魂钱包（战斗胜利发放，商店消费；持久化依赖 Agent3 后端字段，前端先本地维护）
    public int souls = 0;
    // 本局词收集（结算页 chip 展示）
    public final java.util.List<String> runNewWords = new java.util.ArrayList<>();
    public final java.util.List<String> runWrongWords = new java.util.ArrayList<>();
    // GAP-05：本局累计选卡/购卡数（成就 cards5/cards10 计数上报）
    public int runCards = 0;

    private HomeView home;
    private BattleView battle;
    private ResultView result;
    private WrongbookView wrongbook;
    private SettingsView settings;
    private AchievementsView achievements;
    private StatsView stats;
    private AltarView altar;

    // P1 学习数据导出/恢复（V13.7）：Storage Access Framework，免存储权限
    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    public enum Screen { HOME, BATTLE, RESULT, WRONGBOOK, SETTINGS, ACHIEVEMENTS, STATS, ALTAR }
    private Screen screen = Screen.HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        registerSaflaunchers();
        try {
            createApp();
        } catch (Throwable e) {
            Log.e(TAG, "启动异常", e);
            try { App.saveCrashNow(e); } catch (Throwable ignore) {}
            try {
                Toast.makeText(this, "启动异常：" + e.getMessage(), Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {}
        }
    }

    private void createApp() {
        loadPrefs();   // GAP-08：冷启恢复词书/难度/快刷档
        // 上次崩溃自诊断：setContentView 后立即 Toast（不依赖页面渲染）
        final String crash = App.takeCrash();
        if (crash != null && !crash.isEmpty()) {
            Log.e(TAG, "检测到上次崩溃:\n" + crash);
            String shortC = crash.replaceAll("[\\r\\n]+", " → ");
            shortC = shortC.length() > 260 ? shortC.substring(0, 260) + "…" : shortC;
            Toast.makeText(this, "上次启动异常：" + shortC, Toast.LENGTH_LONG).show();
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG0);
        setContentView(root);

        // View 创建全部防御化：任何页面构造失败都不阻断启动
        try { home = new HomeView(this); } catch (Throwable e) { Log.e(TAG, "HomeView 构造失败", e); }
        try { battle = new BattleView(this); } catch (Throwable e) { Log.e(TAG, "BattleView 构造失败", e); }
        try { result = new ResultView(this); } catch (Throwable e) { Log.e(TAG, "ResultView 构造失败", e); }
        try { wrongbook = new WrongbookView(this); } catch (Throwable e) { Log.e(TAG, "WrongbookView 构造失败", e); }
        try { settings = new SettingsView(this); } catch (Throwable e) { Log.e(TAG, "SettingsView 构造失败", e); }
        try { achievements = new AchievementsView(this); } catch (Throwable e) { Log.e(TAG, "AchievementsView 构造失败", e); }
        try { stats = new StatsView(this); } catch (Throwable e) { Log.e(TAG, "StatsView 构造失败", e); }
        try { altar = new AltarView(this); } catch (Throwable e) { Log.e(TAG, "AltarView 构造失败", e); }

        if (crash != null && !crash.isEmpty() && home != null) {
            try { home.onCrashInfo(crash); } catch (Throwable ignore) {}
        }

        try { show(Screen.HOME); } catch (Throwable e) {
            Log.e(TAG, "主页显示失败", e);
            try { Toast.makeText(this, "主页显示失败：" + e.getMessage(), Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
        }

        // 后端后台线程初始化（词库解析 + SQLite），不阻塞 UI
        new Thread(() -> {
            try {
                router = new BackendRouter(getApplicationContext());
                backendReady = true;
                Log.i(TAG, "原生后端就绪（词库/SQLite）");
            } catch (Throwable e) {
                backendErr = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
                Log.e(TAG, "后端初始化失败: " + backendErr, e);
            }
            main.post(() -> {
                try {
                    home.onBackendState(backendReady, backendErr);
                    if (backendReady) home.refreshMeta();
                } catch (Throwable ignore) {}
            });
        }, "wd-backend-init").start();
    }

    public BackendRouter router() {
        return router;
    }

    // ── P1 学习数据导出/恢复（V13.7）─────────────────────────────────────

    /** 在 onCreate 早期注册 SAF launcher（必须在 STARTED 之前）。 */
    private void registerSaflaunchers() {
        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                uri -> {
                    if (uri == null) return;   // 用户取消
                    new Thread(() -> {
                        try {
                            JSONObject backup = router.backup();
                            try (java.io.OutputStream os = getContentResolver().openOutputStream(uri)) {
                                os.write(backup.toString(2).getBytes("UTF-8"));
                            }
                            main.post(() -> Toast.makeText(this, "✅ 学习数据已导出", Toast.LENGTH_SHORT).show());
                        } catch (Throwable e) {
                            Log.e(TAG, "导出失败", e);
                            main.post(() -> Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }, "wd-export").start();
                });
        importLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    new Thread(() -> {
                        String text;
                        try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                            byte[] tmp = new byte[8192];
                            int n;
                            while ((n = is.read(tmp)) > 0) buf.write(tmp, 0, n);
                            text = buf.toString("UTF-8");
                        } catch (Throwable e) {
                            main.post(() -> Toast.makeText(this, "读取备份失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                            return;
                        }
                        try {
                            JSONObject root = new JSONObject(text);
                            main.post(() -> confirmRestore(root));
                        } catch (Throwable e) {
                            main.post(() -> Toast.makeText(this, "备份文件已损坏，已拒绝恢复", Toast.LENGTH_LONG).show());
                        }
                    }, "wd-import").start();
                });
    }

    /** 设置页调用：弹出系统文件选择器导出 JSON。 */
    public void launchExport() {
        if (!backendReady) { Toast.makeText(this, "后端未就绪，请稍候", Toast.LENGTH_SHORT).show(); return; }
        String name = "word-dungeon-backup-" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(new java.util.Date()) + ".json";
        exportLauncher.launch(name);
    }

    /** 设置页调用：弹出系统文件选择器导入 JSON。 */
    public void launchImport() {
        if (!backendReady) { Toast.makeText(this, "后端未就绪，请稍候", Toast.LENGTH_SHORT).show(); return; }
        importLauncher.launch(new String[]{"application/json"});
    }

    /** 恢复是破坏性操作：写库前必须弹确认框；取消则什么都不做。 */
    private void confirmRestore(JSONObject root) {
        int prog = root.optJSONArray("progress") == null ? 0 : root.optJSONArray("progress").length();
        new android.app.AlertDialog.Builder(this)
                .setTitle("恢复学习数据？")
                .setMessage("将从备份恢复 " + prog + " 条单词记录。\n当前所有进度将被覆盖，且不可撤销。")
                .setPositiveButton("恢复", (d, w) -> new Thread(() -> {
                    try {
                        router.restoreBackup(root);
                        main.post(() -> {
                            Toast.makeText(this, "✅ 恢复完成，正在重启…", Toast.LENGTH_SHORT).show();
                            main.postDelayed(this::recreate, 600);
                        });
                    } catch (Throwable e) {
                        Log.e(TAG, "恢复失败", e);
                        main.post(() -> Toast.makeText(this, "恢复失败：" + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }, "wd-restore").start())
                .setNegativeButton("取消", null)
                .show();
    }

    /** V13.0：根容器（彩蛋全屏动画挂载用）。 */
    public FrameLayout root() {
        return root;
    }

    /** GAP-08：冷启恢复用户上次的词书/难度/快刷档选择。 */
    private void loadPrefs() {
        try {
            android.content.SharedPreferences sp =
                    getSharedPreferences("wd_prefs", MODE_PRIVATE);
            book = sp.getString("book", book);
            difficulty = sp.getString("difficulty", difficulty);
            quickLevel = sp.getString("quickLevel", quickLevel);
            // R3：speak 冷启回读（与 SettingsView 的写入键一致：wd_ui/speak，未设置时维持默认 true）
            android.content.SharedPreferences ui =
                    getSharedPreferences("wd_ui", MODE_PRIVATE);
            speak = ui.getBoolean("speak", speak);
        } catch (Throwable ignore) {}
    }

    // ── R3：成就 key → 中文名称映射（与设置页成就墙命名一致），集中定义供 BattleView 答题 toast 与
    // ResultView 结算 toast 复用，避免两处各写一份映射漏 key ──
    private static final String[][] ACH_NAMES = {
            {"boss1", "初战告捷"},
            {"boss2", "穿透迷雾"},
            {"boss3", "征服深渊"},
            {"first_win", "远征通关"},
            {"combo10", "连击高手"},   // 与成就墙 AchievementsView.ACH_DEFS 命名对齐（终期微修）
            {"correct50", "弹无虚发"},
            {"cards5", "初具规模"},
            {"cards10", "词典大成"},
            {"master30", "渐入佳境"},
            {"master60", "词汇达人"},
            {"streak3", "坚持不懈"},
            {"streak7", "一周不辍"},
            {"endless5", "深渊行者"},
            {"endless10", "深渊主宰"},
            {"saint_awakened", "圣者觉醒"},
    };

    /** 成就 key 转中文名；未登记的 key 原样返回，不静默吞掉。 */
    public static String achName(String key) {
        if (key == null || key.isEmpty()) return "";
        try {
            for (String[] e : ACH_NAMES) {
                if (e[0].equals(key)) return e[1];
            }
        } catch (Throwable ignore) {}
        return key;
    }

    /** 设置页改动词书/难度/快刷档时调用，写回 SharedPreferences。 */
    public void savePrefs() {
        try {
            getSharedPreferences("wd_prefs", MODE_PRIVATE).edit()
                    .putString("book", book)
                    .putString("difficulty", difficulty)
                    .putString("quickLevel", quickLevel)
                    .apply();
        } catch (Throwable ignore) {}
    }

    public boolean backendReady() {
        return backendReady;
    }

    public void show(Screen s) {
        screen = s;
        root.removeAllViews();
        switch (s) {
            case HOME:
                if (home != null) {
                    root.addView(home);
                    try { home.refreshMeta(); } catch (Throwable ignore) {}
                }
                break;
            case BATTLE:
                if (battle != null) {
                    // V12.7.10 根治：battle 必须 MATCH_PARENT，否则按内容高(WRAP_CONTENT)布局，
                    // 浮层百分比定位(placeAt)会以内容高为基准，反馈条实际显示在屏高 65% 处
                    root.addView(battle, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    battle.startRun();
                }
                else { TopToast.error(this, "战斗页不可用"); show(Screen.HOME); }
                break;
            case RESULT:
                if (result != null) root.addView(result);
                break;
            case WRONGBOOK:
                if (wrongbook != null) { root.addView(wrongbook); wrongbook.refresh(); }
                break;
            case SETTINGS:
                if (settings != null) {
                    root.addView(settings);
                    try { settings.refresh(); } catch (Throwable ignore) {}
                }
                break;
            case ACHIEVEMENTS:
                if (achievements != null) {
                    root.addView(achievements);
                    try { achievements.refresh(); } catch (Throwable ignore) {}
                }
                break;
            case STATS:
                if (stats != null) {
                    root.addView(stats);
                    try { stats.refresh(); } catch (Throwable ignore) {}
                }
                break;
            case ALTAR:
                if (altar != null) {
                    root.addView(altar);
                    try { altar.refresh(); } catch (Throwable ignore) {}
                }
                break;
        }
    }

    public ResultView result() { return result; }

    public Screen screen() { return screen; }

    /** 返回键：按层级返回；主页默认退出 */
    @Override
    public void onBackPressed() {
        switch (screen) {
            case BATTLE:
                battle.onBack(); return;
            case RESULT:
            case WRONGBOOK:
            case SETTINGS:
            case ACHIEVEMENTS:
            case STATS:
            case ALTAR:
                show(Screen.HOME); return;
            case HOME:
            default:
                super.onBackPressed();
        }
    }

    public Handler main() { return main; }
}

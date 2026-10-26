package com.guzifeng.worddungeon;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.guzifeng.worddungeon.server.BackendRouter;
import com.guzifeng.worddungeon.ui.AchievementsView;
import com.guzifeng.worddungeon.ui.BattleView;
import com.guzifeng.worddungeon.ui.HomeView;
import com.guzifeng.worddungeon.ui.ResultView;
import com.guzifeng.worddungeon.ui.SettingsView;
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

    private HomeView home;
    private BattleView battle;
    private ResultView result;
    private WrongbookView wrongbook;
    private SettingsView settings;
    private AchievementsView achievements;

    public enum Screen { HOME, BATTLE, RESULT, WRONGBOOK, SETTINGS, ACHIEVEMENTS }
    private Screen screen = Screen.HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
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
                if (battle != null) { root.addView(battle); battle.startRun(); }
                else { Toast.makeText(this, "战斗页不可用", Toast.LENGTH_SHORT).show(); show(Screen.HOME); }
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
                show(Screen.HOME); return;
            case HOME:
            default:
                super.onBackPressed();
        }
    }

    public Handler main() { return main; }
}

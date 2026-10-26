package com.guzifeng.worddungeon;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.FrameLayout;

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
    public String difficulty = "adaptive";  // adaptive / 普通 / 困难 / 地狱
    public boolean speak = true;
    public JSONObject bankMeta = null;

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
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG0);
        setContentView(root);

        home = new HomeView(this);
        battle = new BattleView(this);
        result = new ResultView(this);
        wrongbook = new WrongbookView(this);
        settings = new SettingsView(this);
        achievements = new AchievementsView(this);

        show(Screen.HOME);

        // 上次崩溃自诊断
        final String crash = App.takeCrash();
        if (crash != null && !crash.isEmpty()) {
            Log.e(TAG, "检测到上次崩溃:\n" + crash);
            home.onCrashInfo(crash);
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
                home.onBackendState(backendReady, backendErr);
                if (backendReady) home.refreshMeta();
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
            case HOME: root.addView(home); break;
            case BATTLE:
                root.addView(battle);
                battle.startRun();
                break;
            case RESULT: root.addView(result); break;
            case WRONGBOOK: root.addView(wrongbook); wrongbook.refresh(); break;
            case SETTINGS: root.addView(settings); break;
            case ACHIEVEMENTS: root.addView(achievements); break;
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

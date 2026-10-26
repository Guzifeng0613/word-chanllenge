package com.guzifeng.worddungeon.bridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.server.BackendRouter;

import org.json.JSONObject;

/**
 * WebView JavaScriptInterface 直连桥（异步回调版，纸片项目同款成熟模式）。
 * 后端（词库解析/SQLite）在**后台线程**惰性初始化，绝不阻塞主线程/启动；
 * 前端带 15s 自动重试兜底，后端就绪后首个请求即成功。
 * JS 端：window.WordDungeon.call(callId, route, payloadJson)
 * 原生处理完成后：runOnUiThread -> web.evaluateJavascript("window.__wdCb('callId', <json>)")
 */
public class JsBridge {

    private static final String TAG = "WordDungeon";
    private final Context ctx;
    private final WebView web;
    private final MainActivity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile BackendRouter router;
    private volatile String initErr = null;

    public JsBridge(Context ctx, WebView web, MainActivity activity) {
        this.ctx = ctx;
        this.web = web;
        this.activity = activity;
        /* 后台线程初始化后端（词库解析+SQLite），不阻塞启动 */
        new Thread(() -> {
            try {
                router = new BackendRouter(ctx);
                Log.i(TAG, "后端初始化完成（词库/SQLite 就绪）");
            } catch (Throwable e) {
                initErr = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
                Log.e(TAG, "后端初始化失败: " + initErr, e);
            }
        }, "wd-backend-init").start();
    }

    @JavascriptInterface
    public void call(String callId, String route, String payload) {
        String result;
        try {
            BackendRouter r = router;
            if (r == null) {
                result = "ERR:后端初始化中" + (initErr != null ? "（失败:" + initErr + "）" : "，请稍后重试");
            } else {
                JSONObject body = (payload == null || payload.isEmpty()) ? null : new JSONObject(payload);
                JSONObject out = r.dispatch(route, "POST", body);
                result = out.toString();
            }
        } catch (Throwable e) {
            result = "ERR:" + e.getClass().getSimpleName() + ": " +
                    (e.getMessage() == null ? "" : e.getMessage());
        }
        final String js = "window.__wdCb('" + callId + "', " + quote(result) + ");";
        main.post(() -> {
            try {
                web.evaluateJavascript(js, null);
            } catch (Throwable ignore) {}
        });
    }

    /** 退出 App（前端主页返回键默认行为） */
    @JavascriptInterface
    public void exit() {
        main.post(() -> {
            try {
                if (activity != null) activity.finish();
            } catch (Throwable ignore) {}
        });
    }

    /** JSON 字符串安全嵌入 JS 双引号字符串字面量 */
    private static String quote(String s) {
        return JSONObject.quote(s == null ? "" : s);
    }
}

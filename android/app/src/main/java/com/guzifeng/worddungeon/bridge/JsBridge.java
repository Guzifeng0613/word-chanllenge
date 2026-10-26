package com.guzifeng.worddungeon.bridge;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.server.BackendRouter;

import org.json.JSONObject;

/**
 * WebView JavaScriptInterface 直连桥（异步回调版，纸片项目同款成熟模式）。
 * JS 端：window.WordDungeon.call(callId, route, payloadJson)
 * 原生处理完成后：runOnUiThread -> web.evaluateJavascript("window.__wdCb('callId', <json>)")
 * 零网络层、零注入时序问题，长耗时操作不阻塞 JS 线程。
 */
public class JsBridge {

    private final BackendRouter router;
    private final WebView web;
    private final MainActivity activity;
    private final Handler main = new Handler(Looper.getMainLooper());

    public JsBridge(Context ctx, WebView web, MainActivity activity) {
        this.router = new BackendRouter(ctx);
        this.web = web;
        this.activity = activity;
    }

    @JavascriptInterface
    public void call(String callId, String route, String payload) {
        String result;
        try {
            JSONObject body = (payload == null || payload.isEmpty()) ? null : new JSONObject(payload);
            JSONObject out = router.dispatch(route, "POST", body);
            result = out.toString();
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

package com.guzifeng.worddungeon;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.guzifeng.worddungeon.bridge.JsBridge;
import com.guzifeng.worddungeon.server.LocalServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * 主入口（V9.2 架构重构）：
 * 不再依赖 Capacitor 的 BridgeActivity（其 getBridge() 初始化时机导致 JS 桥注册不可靠），
 * 改为自建 WebView + shouldInterceptRequest 加载本地页面 + addJavascriptInterface 直连桥。
 * 与「纸片」项目同构：WebView + JS 桥是几十个真机版本验证过的成熟路径。
 * 本地服务（NanoHTTPD/SQLite）保留，供浏览器/调试用；App 内主通道是 JS 桥。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WordDungeon";
    private static final String APP_HOST = "https://app.local/";
    public static MainActivity instance = null;
    private WebView web;
    private LocalServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        try {
            web = new WebView(this);
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            s.setSafeBrowsingEnabled(false);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setDefaultTextEncodingName("UTF-8");
            s.setTextZoom(100);
            web.setWebViewClient(new WebViewClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (request != null && "app.local".equals(request.getUrl() != null ? request.getUrl().getHost() : null)) {
                        try {
                            InputStream is = getAssets().open("public/index.html");
                            return new WebResourceResponse("text/html", "UTF-8", is);
                        } catch (IOException e) {
                            Log.e(TAG, "读取页面失败", e);
                            return new WebResourceResponse("text/plain", "UTF-8",
                                    new ByteArrayInputStream("页面加载失败".getBytes()));
                        }
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });
            // 直连桥：前端 window.WordDungeon.call(callId, route, payload) 直调 Java 后端
            web.addJavascriptInterface(new JsBridge(getApplicationContext(), web, this), "WordDungeon");
            Log.i(TAG, "JavaScriptInterface 直连桥已注册（window.WordDungeon）");
            web.loadUrl(APP_HOST);
            setContentView(web);
        } catch (Throwable e) {
            Log.e(TAG, "WebView 初始化失败", e);
        }
        // 本地 HTTP 服务（浏览器/调试通道；App 内主通道为 JS 桥）
        new Thread(this::startLocalServer, "worddungeon-backend").start();
    }

    private void startLocalServer() {
        int port = 8080;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                server = new LocalServer(getApplicationContext(), port);
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.i(TAG, "本地后端已启动 127.0.0.1:" + port);
                return;
            } catch (Throwable e) {
                Log.e(TAG, "端口 " + port + " 启动失败: " + e, e);
                try { if (server != null) server.stop(); } catch (Throwable ignore) {}
                server = null;
                port++;
            }
        }
        Log.e(TAG, "本地后端全部端口启动失败");
    }

    /** 返回键：交给前端按层级处理（主页默认=退出，其余返回上一级）；前端无处理时退出 */
    @Override
    public void onBackPressed() {
        if (web != null) {
            try {
                web.evaluateJavascript("window.__wdBack && window.__wdBack()", null);
                return;
            } catch (Throwable ignore) {}
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) { server.stop(); server = null; }
        if (web != null) { web.destroy(); web = null; }
        instance = null;
    }
}

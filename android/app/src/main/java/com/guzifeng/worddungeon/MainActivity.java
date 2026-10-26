package com.guzifeng.worddungeon;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.guzifeng.worddungeon.bridge.JsBridge;
import com.guzifeng.worddungeon.server.LocalServer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import fi.iki.elonen.NanoHTTPD;

/**
 * 主入口（V9.2 架构重构 + 启动加固）：
 * 自建 WebView + shouldInterceptRequest 加载本地页面 + addJavascriptInterface 直连桥（纸片同构）。
 * 加固：① 全流程 try/catch，失败 Toast 而非崩溃；② 未捕获异常写 crash.log，下次启动页面自诊断。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "WordDungeon";
    private static final String APP_HOST = "https://app.local/";
    private static final String CRASH_LOG = "crash.log";
    public static MainActivity instance = null;
    private WebView web;
    private LocalServer server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                saveCrash(e);
            } catch (Throwable ignore) {}
        });
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
                    if (request != null && request.getUrl() != null && "app.local".equals(request.getUrl().getHost())) {
                        try {
                            InputStream is = getAssets().open("public/index.html");
                            return new WebResourceResponse("text/html", "UTF-8", is);
                        } catch (IOException e) {
                            return new WebResourceResponse("text/plain", "UTF-8",
                                    new ByteArrayInputStream("页面加载失败".getBytes()));
                        }
                    }
                    return super.shouldInterceptRequest(view, request);
                }
            });
            web.addJavascriptInterface(new JsBridge(getApplicationContext(), web, this), "WordDungeon");
            setContentView(web);
            web.loadUrl(APP_HOST);
            Log.i(TAG, "WebView 就绪，JS 桥已注册（window.WordDungeon）");
            // 上次崩溃自诊断
            final String crash = readCrash();
            if (crash != null && !crash.isEmpty()) {
                Log.e(TAG, "检测到上次崩溃:\n" + crash);
                web.postDelayed(() -> {
                    try { Toast.makeText(this, "上次启动异常：" + crash, Toast.LENGTH_LONG).show(); } catch (Throwable ignore) {}
                }, 2000);
                deleteCrash();
            }
        } catch (Throwable e) {
            Log.e(TAG, "WebView 初始化失败", e);
            try { saveCrash(e); } catch (Throwable ignore) {}
            try {
                Toast.makeText(this, "初始化失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {}
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

    private void saveCrash(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String text = Thread.currentThread().getName() + " :: " + sw.toString();
        try {
            File f = new File(getFilesDir(), CRASH_LOG);
            try (FileOutputStream os = new FileOutputStream(f, true)) {
                os.write(text.getBytes("UTF-8"));
                os.write('\n');
            }
        } catch (Throwable ignore) {}
    }

    private String readCrash() {
        try {
            File f = new File(getFilesDir(), CRASH_LOG);
            if (!f.exists()) return null;
            byte[] b = new byte[(int) Math.min(f.length(), 2000)];
            try (InputStream is = new FileInputStream(f)) {
                int n = is.read(b);
                return n > 0 ? new String(b, 0, n, "UTF-8") : null;
            }
        } catch (Throwable ignore) {
            return null;
        }
    }

    private void deleteCrash() {
        try {
            File f = new File(getFilesDir(), CRASH_LOG);
            if (f.exists()) f.delete();
        } catch (Throwable ignore) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) { server.stop(); server = null; }
        if (web != null) { web.destroy(); web = null; }
        instance = null;
    }
}

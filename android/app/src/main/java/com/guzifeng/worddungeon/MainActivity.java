package com.guzifeng.worddungeon;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import com.getcapacitor.BridgeActivity;
import com.guzifeng.worddungeon.bridge.JsBridge;
import com.guzifeng.worddungeon.bridge.WordDungeonPlugin;
import com.guzifeng.worddungeon.server.LocalServer;

import fi.iki.elonen.NanoHTTPD;

/**
 * 主入口：Capacitor 壳 + 启动本地 Java 后端（NanoHTTPD，127.0.0.1:8080）
 * 服务在后台线程启动，解析词库不阻塞 UI；前端默认走 WordDungeon 插件桥（App 内），
 * HTTP 服务保留供浏览器调试/未来云端。
 */
public class MainActivity extends BridgeActivity {

    public static final int LOCAL_PORT = 8080;
    private static final String TAG = "WordDungeon";
    private LocalServer server;
    /** 后端最终状态：ok / 失败原因（供诊断） */
    public static volatile String backendStatus = "starting";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 注册本地插件桥（前端 Capacitor.Plugins.WordDungeon 直接调 Java 后端）
        try {
            registerPlugin(WordDungeonPlugin.class);
            Log.i(TAG, "WordDungeon 插件桥已注册");
        } catch (Throwable e) {
            Log.e(TAG, "注册插件桥失败", e);
        }
        // 页面为 http://localhost，fetch http://127.0.0.1 需放行混合内容（HTTP 调试通道用）
        try {
            WebView wv = getBridge() != null ? getBridge().getWebView() : null;
            if (wv != null) {
                wv.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                // 方案 B：JavaScriptInterface 直连桥（前端 window.WordDungeon.call 直调 Java 后端）
                wv.addJavascriptInterface(new JsBridge(getApplicationContext(), wv), "WordDungeon");
                Log.i(TAG, "JavaScriptInterface 直连桥已注册（window.WordDungeon）");
            }
        } catch (Throwable e) {
            Log.w(TAG, "setMixedContentMode/addJavascriptInterface failed", e);
        }
        new Thread(this::startLocalServer, "worddungeon-backend").start();
    }

    private void startLocalServer() {
        int port = LOCAL_PORT;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                server = new LocalServer(getApplicationContext(), port);
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                backendStatus = "ok:" + port;
                Log.i(TAG, "本地后端已启动 127.0.0.1:" + port);
                toast("本地后端已启动 :" + port);
                return;
            } catch (Throwable e) {
                Log.e(TAG, "端口 " + port + " 启动失败: " + e, e);
                backendStatus = "fail:" + port + ":" + e.getClass().getSimpleName()
                        + ":" + (e.getMessage() == null ? "" : e.getMessage());
                try { if (server != null) server.stop(); } catch (Throwable ignore) {}
                server = null;
                port++;
            }
        }
        Log.e(TAG, "本地后端 10 个端口全部启动失败, status=" + backendStatus);
        final String st = backendStatus;
        toast("后端启动失败：" + st);
    }

    private void toast(final String msg) {
        runOnUiThread(() -> {
            try {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignore) {}
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
            server = null;
        }
    }
}

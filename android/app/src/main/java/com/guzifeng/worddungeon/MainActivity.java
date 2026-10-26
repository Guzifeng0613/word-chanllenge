package com.guzifeng.worddungeon;

import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;
import com.guzifeng.worddungeon.server.LocalServer;

import fi.iki.elonen.NanoHTTPD;

/**
 * 主入口：Capacitor 壳 + 启动本地 Java 后端（NanoHTTPD，127.0.0.1:8080）
 * 服务在后台线程启动，解析词库不阻塞 UI；前端通过 http://127.0.0.1:8080 调用。
 */
public class MainActivity extends BridgeActivity {

    public static final int LOCAL_PORT = 8080;
    private static final String TAG = "WordDungeon";
    private LocalServer server;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 页面为 http://localhost，fetch http://127.0.0.1 需放行混合内容
        try {
            WebView wv = getBridge() != null ? getBridge().getWebView() : null;
            if (wv != null) {
                wv.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                Log.i(TAG, "mixed content allowed for local backend");
            }
        } catch (Exception e) {
            Log.w(TAG, "setMixedContentMode failed", e);
        }
        new Thread(this::startLocalServer, "worddungeon-backend").start();
    }

    private void startLocalServer() {
        int port = LOCAL_PORT;
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                server = new LocalServer(getApplicationContext(), port);
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                Log.i(TAG, "本地后端已启动 127.0.0.1:" + port);
                return;
            } catch (Exception e) {
                Log.e(TAG, "端口 " + port + " 启动失败: " + e.getMessage(), e);
                port++;
            }
        }
        Log.e(TAG, "本地后端 8 个端口全部启动失败");
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

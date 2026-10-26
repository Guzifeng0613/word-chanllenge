package com.guzifeng.worddungeon;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;
import com.guzifeng.worddungeon.server.LocalServer;

import fi.iki.elonen.NanoHTTPD;

/**
 * 主入口：Capacitor 壳 + 启动本地 Java 后端（NanoHTTPD，127.0.0.1:8080）
 * 服务在后台线程启动，解析词库不阻塞 UI；前端通过 http://127.0.0.1:8080 调用。
 */
public class MainActivity extends BridgeActivity {

    public static final int LOCAL_PORT = 8080;
    private LocalServer server;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(this::startLocalServer, "worddungeon-backend").start();
    }

    private void startLocalServer() {
        int port = LOCAL_PORT;
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                server = new LocalServer(getApplicationContext(), port);
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                return;
            } catch (Exception e) {
                port++;
            }
        }
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

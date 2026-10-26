package com.guzifeng.worddungeon.bridge;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import com.guzifeng.worddungeon.server.BackendRouter;

import org.json.JSONObject;

/**
 * App 内前后端桥：前端通过 Capacitor.Plugins.WordDungeon.call({route, payload})
 * 直接调用 Java 后端，绕开 HTTP/端口/cleartext/CORS/mixed-content 整条网络链路。
 * 后端逻辑仍 100% 在 Java（BackendRouter 与 HTTP 通道共用）。
 */
@CapacitorPlugin(name = "WordDungeon")
public class WordDungeonPlugin extends Plugin {

    private volatile BackendRouter router;

    private BackendRouter router() {
        if (router == null) {
            synchronized (this) {
                if (router == null) router = new BackendRouter(getContext());
            }
        }
        return router;
    }

    @PluginMethod
    public void call(PluginCall call) {
        String route = call.getString("route");
        if (route == null || route.isEmpty()) {
            call.reject("缺少 route");
            return;
        }
        JSONObject payload = call.getObject("payload");
        try {
            String method = "GET";
            String r = route;
            int q = r.indexOf('?');
            String query = null;
            if (q >= 0) {
                query = r.substring(q + 1);
                r = r.substring(0, q);
            }
            if (query != null && payload == null) {
                // GET 查询参数（book 等）并入 payload
                payload = new JSONObject();
                for (String kv : query.split("&")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        payload.put(java.net.URLDecoder.decode(kv.substring(0, eq), "UTF-8"),
                                java.net.URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
                    }
                }
            }
            if (!"/auth/guest".equals(r) && !"/bank/meta".equals(r)
                    && !"/review/due".equals(r) && !"/wrongbook".equals(r)
                    && !"/stats/daily".equals(r) && !"/stats/book".equals(r)) {
                method = "POST";
            }
            JSONObject out = router().dispatch(r, method, payload);
            call.resolve(new JSObject(out.toString()));
        } catch (Throwable e) {
            call.reject(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}

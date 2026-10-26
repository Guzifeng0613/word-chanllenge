package com.guzifeng.worddungeon.server;

import android.content.Context;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * 本地 HTTP 服务（浏览器/调试/未来云端通道），路由逻辑全部委托 BackendRouter。
 * App 内正式通道是 WordDungeonPlugin 插件桥；本服务保留 127.0.0.1 HTTP 能力。
 */
public class LocalServer extends NanoHTTPD {

    private final BackendRouter router;

    public LocalServer(Context ctx, int port) {
        super("127.0.0.1", port);
        this.router = new BackendRouter(ctx);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        try {
            JSONObject body = null;
            if (Method.POST.equals(method)) {
                Map<String, String> m = new HashMap<>();
                session.parseBody(m);
                String data = m.get("postData");
                body = data == null ? new JSONObject() : new JSONObject(data);
            }
            JSONObject out = router.dispatch(uri, method.name(), body);
            return json(Response.Status.OK, out);
        } catch (Exception e) {
            return json(Response.Status.INTERNAL_ERROR, err("服务异常: " + e.getMessage()));
        }
    }

    private static JSONObject err(String msg) {
        JSONObject o = new JSONObject();
        try { o.put("detail", msg); } catch (Exception ignore) {}
        return o;
    }

    private Response json(Response.Status status, JSONObject o) {
        Response r = newFixedLengthResponse(status, "application/json", o.toString());
        r.addHeader("Access-Control-Allow-Origin", "*");
        return r;
    }
}

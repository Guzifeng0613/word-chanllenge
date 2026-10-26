package com.guzifeng.worddungeon;

import android.app.Application;
import android.content.Context;
import android.util.Log;

/** 全局 Application：持有进程级 Context（供 UI 工具与后端引擎使用） */
public class App extends Application {
    private static final String TAG = "WordDungeon";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Log.e(TAG, "未捕获异常 @" + t.getName(), e);
        });
    }

    public static Context ctx() {
        return instance;
    }
}

package com.guzifeng.worddungeon;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** 全局 Application：持进程级 Context；未捕获异常落盘 crash.log（下次启动自诊断显示） */
public class App extends Application {
    private static final String TAG = "WordDungeon";
    private static final String CRASH_LOG = "crash.log";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String text = "[" + java.text.SimpleDateFormat.getDateTimeInstance().format(new java.util.Date())
                        + "] " + t.getName() + " :: " + sw;
                File f = new File(getFilesDir(), CRASH_LOG);
                try (FileOutputStream os = new FileOutputStream(f, true)) {
                    os.write(text.getBytes("UTF-8"));
                    os.write('\n');
                }
                Log.e(TAG, "未捕获异常已落盘", e);
            } catch (Throwable ignore) {}
        });
    }

    /** 读取最近一次崩溃摘要（返回后删除），供 UI 自诊断 */
    public static String takeCrash() {
        try {
            File f = new File(instance.getFilesDir(), CRASH_LOG);
            if (!f.exists()) return null;
            byte[] b = new byte[(int) Math.min(f.length(), 4000)];
            try (java.io.InputStream is = new java.io.FileInputStream(f)) {
                int n = is.read(b);
                if (n <= 0) { f.delete(); return null; }
                f.delete();
                return new String(b, 0, n, "UTF-8");
            }
        } catch (Throwable ignore) {
            return null;
        }
    }

    /** 立即写入崩溃（供 onCreate 兜底 catch 调用） */
    public static void saveCrashNow(Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String text = "[" + java.text.SimpleDateFormat.getDateTimeInstance().format(new java.util.Date())
                    + "] " + Thread.currentThread().getName() + " :: " + sw;
            File f = new File(instance.getFilesDir(), CRASH_LOG);
            try (FileOutputStream os = new FileOutputStream(f, true)) {
                os.write(text.getBytes("UTF-8"));
                os.write('\n');
            }
        } catch (Throwable ignore) {}
    }

    public static Context ctx() {
        return instance;
    }
}

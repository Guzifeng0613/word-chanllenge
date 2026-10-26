package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/** 主页：模式 / 词书 / 难度 / 开始 / 错词本·成就·设置入口 */
public class HomeView extends LinearLayout {

    private final MainActivity act;
    private TextView bookBtn, diffBtn, statusTxt;
    private LinearLayout bookRow, diffRow;
    private TextView startBtn, wrongBtn, achBtn, setBtn;

    private String[] bookIds = {"gaokao", "cet4", "cet6", "kaoyan", "all"};
    private String[] bookNames = {"高考", "四级", "六级", "考研", "全部"};
    private String[] diffIds = {"adaptive", "easy", "normal", "hard", "hell"};
    private String[] diffNames = {"自适应", "普通", "困难", "地狱", "巅峰"};

    public HomeView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(30), dp(18), dp(20));

        TextView title = Ui.text(c, "⚔ 单词地下城", 26, Ui.GOLD, true);
        addView(title);
        addView(Ui.text(c, "应试单词 · 战斗 Roguelike", 13, Ui.MUT, false));
        addView(spacer(6));

        statusTxt = Ui.text(c, "后端加载中…", 13, Ui.ORANGE, false);
        addView(statusTxt);
        addView(spacer(10));

        // 词书
        bookRow = new LinearLayout(c);
        bookRow.setOrientation(HORIZONTAL);
        bookRow.setGravity(Gravity.CENTER_VERTICAL);
        bookRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        bookRow.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        bookBtn = Ui.text(c, "词书：高考", 15, Ui.TXT, true);
        bookRow.addView(bookBtn, lp(0, -2, 1f));
        addView(bookRow);
        addView(spacer(8));

        // 难度
        diffRow = new LinearLayout(c);
        diffRow.setOrientation(HORIZONTAL);
        diffRow.setGravity(Gravity.CENTER_VERTICAL);
        diffRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        diffRow.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        diffBtn = Ui.text(c, "难度：自适应", 15, Ui.TXT, true);
        diffRow.addView(diffBtn, lp(0, -2, 1f));
        addView(diffRow);
        addView(spacer(16));

        // 模式选择
        addView(Ui.text(c, "选择模式", 14, Ui.MUT, true));
        addView(spacer(6));
        LinearLayout modes = new LinearLayout(c);
        modes.setOrientation(HORIZONTAL);
        addModeBtn(modes, "⚔ 远征", "campaign", "3 层闯关");
        addModeBtn(modes, "🌀 无尽", "endless", "无限深渊");
        addModeBtn(modes, "⚡ 快刷", "quick", "刷词冲刺");
        addView(modes);
        addView(spacer(18));

        startBtn = Ui.button(c, "开始战斗", Ui.CYAN, Ui.BG0, true);
        startBtn.setOnClickListener(v -> { act.mode = pickMode; act.show(MainActivity.Screen.BATTLE); });
        addView(startBtn);
        addView(spacer(18));

        // 入口
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        wrongBtn = Ui.button(c, "📕 错词本", Ui.CARD2, Ui.TXT, false);
        achBtn = Ui.button(c, "🏆 成就", Ui.CARD2, Ui.TXT, false);
        setBtn = Ui.button(c, "⚙ 设置", Ui.CARD2, Ui.TXT, false);
        wrongBtn.setOnClickListener(v -> act.show(MainActivity.Screen.WRONGBOOK));
        achBtn.setOnClickListener(v -> act.show(MainActivity.Screen.ACHIEVEMENTS));
        setBtn.setOnClickListener(v -> act.show(MainActivity.Screen.SETTINGS));
        row.addView(wrongBtn, lp(0, -2, 1f));
        row.addView(achBtn, lp(0, -2, 1f));
        row.addView(setBtn, lp(0, -2, 1f));
        addView(row);

        // 词书/难度点击 → 循环切换
        int[] bi = {0};
        bookRow.setOnClickListener(v -> {
            bi[0] = (bi[0] + 1) % bookIds.length;
            act.book = bookIds[bi[0]];
            bookBtn.setText("词书：" + bookNames[bi[0]]);
        });
        int[] di = {0};
        diffRow.setOnClickListener(v -> {
            di[0] = (di[0] + 1) % diffIds.length;
            act.difficulty = diffIds[di[0]];
            diffBtn.setText("难度：" + diffNames[di[0]]);
        });
    }

    private String pickMode = "campaign";

    private void addModeBtn(LinearLayout parent, String label, String id, String sub) {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(12), dp(8), dp(12));
        box.setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
        TextView t = Ui.text(c, label, 14, Ui.TXT, true);
        TextView s = Ui.text(c, sub, 11, Ui.MUT, false);
        box.addView(t);
        box.addView(s);
        int target = modeMap(id);
        box.setOnClickListener(v -> {
            pickMode = id;
            parent.getChildCount();
            for (int i = 0; i < parent.getChildCount(); i++) {
                parent.getChildAt(i).setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
            }
            box.setBackground(Ui.card(12, Ui.BG1, 2, Ui.CYAN));
        });
        if (target == 0) box.setBackground(Ui.card(12, Ui.BG1, 2, Ui.CYAN));
        parent.addView(box, lp(0, -2, 1f));
    }

    private int modeMap(String id) {
        if ("campaign".equals(id)) return 0;
        if ("endless".equals(id)) return 1;
        return 2;
    }

    public void onBackendState(boolean ready, String err) {
        statusTxt.setText(ready ? "● 后端已就绪 · 词书加载中" : "● 后端异常：" + (err == null ? "未知" : err));
        statusTxt.setTextColor(ready ? Ui.GREEN : Ui.RED);
    }

    public void refreshMeta() {
        try {
            JSONObject meta = act.router().dispatch("/bank/meta", "GET", null);
            act.bankMeta = meta;
            JSONObject counts = meta.getJSONObject("counts");
            int total = meta.getInt("total");
            JSONArray books = meta.getJSONArray("books");
            StringBuilder sb = new StringBuilder("● 后端就绪 · 词库共 " + total + " 词 · ");
            for (int i = 0; i < books.length(); i++) {
                JSONObject b = books.getJSONObject(i);
                if (i > 0) sb.append("  ");
                sb.append(b.getString("name")).append(" ").append(counts.optInt(b.getString("id"), 0));
            }
            statusTxt.setText(sb.toString());
            statusTxt.setTextColor(Ui.GREEN);
        } catch (Throwable e) {
            statusTxt.setText("● 词库加载失败：" + e.getMessage());
            statusTxt.setTextColor(Ui.RED);
        }
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
    private LinearLayout.LayoutParams lp(int w, int h, float wgt) {
        return new LinearLayout.LayoutParams(
                w == -2 ? LinearLayout.LayoutParams.WRAP_CONTENT : w,
                h == -2 ? LinearLayout.LayoutParams.WRAP_CONTENT : (h == -1 ? LinearLayout.LayoutParams.MATCH_PARENT : h),
                wgt);
    }
}

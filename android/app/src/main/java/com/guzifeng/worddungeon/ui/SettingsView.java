package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

/** 设置：词书 / 难度 / 发音开关（点按循环切换） */
public class SettingsView extends LinearLayout {

    private final MainActivity act;
    private TextView bookBtn, diffBtn, speakBtn;

    private final String[] bookIds = {"gaokao", "cet4", "cet6", "kaoyan", "all"};
    private final String[] bookNames = {"高考", "四级", "六级", "考研", "全部"};
    private final String[] diffIds = {"adaptive", "easy", "normal", "hard", "hell"};
    private final String[] diffNames = {"自适应", "普通", "困难", "地狱", "巅峰"};

    public SettingsView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(20), dp(16), dp(16));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(c, "⚙ 设置", 20, Ui.GOLD, true);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView back = Ui.text(c, "← 返回", 14, Ui.CYAN, true);
        back.setPadding(dp(8), dp(6), dp(8), dp(6));
        back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        top.addView(back);
        addView(top);
        addView(spacer(14));

        int[] bi = {Math.max(0, indexOf(bookIds, act.book))};
        LinearLayout bookBox = row("📚 词书：" + bookNames[bi[0]]);
        bookBtn = (TextView) bookBox.getChildAt(0);
        addView(bookBox);
        addView(spacer(8));
        bookBox.setOnClickListener(v -> {
            bi[0] = (bi[0] + 1) % bookIds.length;
            act.book = bookIds[bi[0]];
            bookBtn.setText("📚 词书：" + bookNames[bi[0]]);
        });

        int[] di = {Math.max(0, indexOf(diffIds, act.difficulty))};
        LinearLayout diffBox = row("🎚 难度：" + diffNames[di[0]]);
        diffBtn = (TextView) diffBox.getChildAt(0);
        addView(diffBox);
        addView(spacer(8));
        diffBox.setOnClickListener(v -> {
            di[0] = (di[0] + 1) % diffIds.length;
            act.difficulty = diffIds[di[0]];
            diffBtn.setText("🎚 难度：" + diffNames[di[0]]);
        });

        LinearLayout speakBox = row("🔊 单词发音：" + (act.speak ? "开" : "关"));
        speakBtn = (TextView) speakBox.getChildAt(0);
        addView(speakBox);
        addView(spacer(8));
        speakBox.setOnClickListener(v -> {
            act.speak = !act.speak;
            speakBtn.setText("🔊 单词发音：" + (act.speak ? "开" : "关"));
        });

        addView(spacer(12));
        TextView note = Ui.text(c, "词书基于开源 ECDICT 词库 · 释义取常用义 · 难度自适应θ", 12, Ui.MUT, false);
        addView(note);
    }

    private int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return -1;
    }

    private LinearLayout row(String label) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(14), dp(12), dp(14));
        box.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        box.setClickable(true);
        box.setFocusable(true);
        TextView vb = Ui.text(getContext(), label, 15, Ui.TXT, true);
        box.addView(vb);
        return box;
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

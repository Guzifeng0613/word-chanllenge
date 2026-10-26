package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.core.RunState;

import org.json.JSONObject;

/** 结算页（V7.0：result-face + level-card + statgrid + 新学/错词 chips + 金色再来一局） */
public class ResultView extends LinearLayout {

    private final MainActivity act;
    private LinearLayout body;
    private TextView bigTitle, bigSub, bestTxt;
    private LinearLayout statGridHost, wordCardHost;
    private TextView againBtn;
    private boolean win = false;
    private String mode = "campaign";
    private int layer = 1;
    private int wrong = 0;
    private int combo = 0;
    private int questions = 0;
    private int correct = 0;
    private int maxCombo = 0;
    private int kills = 0;

    public ResultView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(16), dp(24), dp(16), dp(20));
        sv.addView(body);
        addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void onFinish(boolean w, String m, int lay, int wr, int cb,
                         int q, int cr, int mc, int k) {
        win = w;
        mode = m;
        layer = lay;
        wrong = wr;
        combo = cb;
        questions = q;
        correct = cr;
        maxCombo = mc;
        kills = k;
        rebuild();
        if (w) Ui.vibrate(getContext(), new long[]{0, 60, 50, 90, 60, 120});   // 胜利结算反馈
        // 后端结算：每日目标 / 无尽纪录 / 成就
        new Thread(() -> {
            try {
                JSONObject b = new JSONObject();
                b.put("mode", mode);
                b.put("layer", layer);
                b.put("win", win);
                b.put("max_combo", maxCombo);
                b.put("correct", correct);
                act.router().dispatch("/progress/finish", "POST", b);
            } catch (Throwable ignore) {}
        }).start();
    }

    private void rebuild() {
        if (body.getChildCount() > 0) body.removeAllViews();
        boolean endless = "endless".equals(mode);
        boolean quick = "quick".equals(mode);
        int acc = questions > 0 ? Math.round(correct * 100f / questions) : 0;

        // result-face
        LinearLayout face = new LinearLayout(getContext());
        face.setOrientation(VERTICAL);
        face.setGravity(Gravity.CENTER);
        face.setPadding(dp(16), dp(18), dp(16), dp(18));
        face.setBackground(Ui.card(16, win ? Ui.CARD : Ui.CARD2, 2, win ? Ui.GOLD : Ui.RED));
        bigTitle = Ui.text(getContext(), "", 22, Ui.TXT, true);
        bigTitle.setGravity(Gravity.CENTER);
        bigSub = Ui.text(getContext(), "", 13, Ui.MUT, false);
        bigSub.setGravity(Gravity.CENTER);
        face.addView(bigTitle);
        bigSub.setPadding(0, dp(6), 0, 0);
        face.addView(bigSub);
        body.addView(face);
        body.addView(spacer(10));

        // level-card
        LinearLayout lv = new LinearLayout(getContext());
        lv.setOrientation(LinearLayout.HORIZONTAL);
        lv.setGravity(Gravity.CENTER_VERTICAL);
        lv.setPadding(dp(12), dp(10), dp(12), dp(10));
        lv.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        TextView badge = Ui.goldBadge(getContext(), "1", 40);
        lv.addView(badge);
        LinearLayout lvinfo = new LinearLayout(getContext());
        lvinfo.setOrientation(LinearLayout.VERTICAL);
        TextView ll = Ui.text(getContext(), "词灵使 Lv.1 · 0/120 XP", 12, Ui.MUT, false);
        LinearLayout barl = new LinearLayout(getContext());
        barl.setOrientation(LinearLayout.HORIZONTAL);
        lvinfo.addView(ll);
        lvinfo.addView(barl);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(0, -2, 1f);
        vl.leftMargin = dp(10);
        lvinfo.setLayoutParams(vl);
        lv.addView(lvinfo);
        body.addView(lv);
        body.addView(spacer(10));

        // statgrid
        statGridHost = new LinearLayout(getContext());
        statGridHost.setOrientation(LinearLayout.HORIZONTAL);
        addStat(statGridHost, questions + "", "答题数");
        addStat(statGridHost, acc + "%", "正确率");
        addStat(statGridHost, maxCombo + "", "最高连击");
        if (!endless) addStat(statGridHost, String.valueOf(Math.max(0, questions - wrong)), "新学词");
        if (endless) addStat(statGridHost, kills + "", "击败敌人");
        body.addView(statGridHost);
        body.addView(spacer(10));

        // 词卡（新学/错词）
        wordCardHost = new LinearLayout(getContext());
        wordCardHost.setOrientation(LinearLayout.VERTICAL);
        wordCardHost.setPadding(dp(14), dp(12), dp(14), dp(12));
        wordCardHost.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        wordCardHost.addView(Ui.text(getContext(), "本次新学的词", 13, Ui.TXT, true));
        TextView newChips = chipsOf(correct > 0 ? "答对 " + correct + " 题 · 新词已入进度" : "无", Ui.CYAN);
        wordCardHost.addView(newChips);
        wordCardHost.addView(spacer(8));
        wordCardHost.addView(Ui.text(getContext(), "仍需复习的错词", 13, Ui.TXT, true));
        TextView wrongChips = chipsOf(wrong > 0 ? wrong + " 词已记入错词本" : "无", Ui.RED);
        wordCardHost.addView(wrongChips);
        body.addView(wordCardHost);
        body.addView(spacer(14));

        // 金色再来一局
        againBtn = Ui.text(getContext(), endless ? "再战深渊" : win ? "再来一局" : "再战一局", 16.5f, 0xFF081326, true);
        againBtn.setGravity(Gravity.CENTER);
        againBtn.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFCD34D, 0xFFF59E0B});
        g.setCornerRadius(dp(13));
        againBtn.setBackground(g);
        againBtn.setOnClickListener(v -> act.show(MainActivity.Screen.BATTLE));
        body.addView(againBtn);
        body.addView(spacer(8));
        TextView home = Ui.text(getContext(), "返回主页", 13, Ui.MUT, true);
        home.setGravity(Gravity.CENTER);
        home.setPadding(dp(10), dp(10), dp(10), dp(10));
        home.setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
        home.setClickable(true);
        home.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        body.addView(home);

        // 填充数据
        if (endless) {
            bigTitle.setText("无尽挑战 · 抵达第 " + layer + " 层");
            bigSub.setText(win ? "新纪录！你比以往任何一次都潜得更深。" : "深渊终将吞噬所有词灵使，但错词已为你保留。");
        } else if (quick) {
            bigTitle.setText(win ? "快刷完成！" : "快刷中断");
            bigSub.setText(win ? "高频词快速刷完一轮，继续巩固！" : "别灰心，错词已经全部保留。");
        } else {
            bigTitle.setText(win ? "远征通关！" : "远征失败");
            bigSub.setText(win ? "你击败了三层词魇，词库又厚了一页。" : "别灰心，错词已经全部保留，会在复习中再出现。");
        }
        fillLevelCard(badge, ll, barl);
    }

    private void addStat(LinearLayout row, String v, String l) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(6), dp(12), dp(6), dp(12));
        cell.setBackground(Ui.card(12, Ui.BG1, 1, Ui.LINE));
        TextView vv = Ui.text(getContext(), v, 20, Ui.TXT, true);
        TextView ll = Ui.text(getContext(), l, 10.5f, Ui.MUT, false);
        cell.addView(vv);
        cell.addView(ll);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(spacer(6));
    }

    private TextView chipsOf(String text, int color) {
        TextView t = Ui.text(getContext(), text, 12, color, false);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    private void fillLevelCard(TextView badge, TextView ll, LinearLayout barl) {
        try {
            JSONObject stats = act.router().dispatch("/stats/daily", "GET", null);
            int level = stats.optInt("level", 1);
            int xp = stats.optInt("xp", 0);
            int xpNeed = stats.optInt("xp_needed", 120);
            badge.setText(String.valueOf(level));
            ll.setText("词灵使 Lv." + level + " · " + xp + "/" + xpNeed + " XP");
            View[] bar = Ui.bar(getContext(), (float) xp / Math.max(1, xpNeed), 0xFF22C55E, 0xFF4ADE80, 6);
            barl.addView(bar[0]);
        } catch (Throwable ignore) {}
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

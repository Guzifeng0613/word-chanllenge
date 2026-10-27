package com.guzifeng.worddungeon.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

/**
 * 结算页（V11.3：FrameLayout 承载 body + 庆祝粒子层；模块逐项入场、数据计数、胜利彩带）。
 */
public class ResultView extends FrameLayout {

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
        setBackground(new AmbientBackground(28));   // V11.3 地下城氛围底

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(16), dp(24), dp(16), dp(20));
        sv.addView(body);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
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
        if (w) {
            Ui.vibrate(getContext(), new long[]{0, 60, 50, 90, 60, 120});
            Sfx.play(getContext(), Sfx.WIN);   // V11.8 胜利琶音
            postDelayed(this::confetti, 350);   // V11.3 胜利彩带
        } else {
            Sfx.play(getContext(), Sfx.FAIL);  // V11.8 失败下行音
        }
        // 后端结算：每日目标 / 无尽纪录 / 成就（R3：不再吞响应——解析本局新解锁成就并在结算页中文 toast + Sfx）
        new Thread(() -> {
            try {
                // /progress/finish 响应只回全量 ach 数组、不带 new_ach（以实际响应结构为准）：
                // 先快照基线，finish 后 diff 出本局新解锁的成就 id；全程 opt + try/catch，异常数据不闪退。
                JSONArray before = null;
                try {
                    JSONObject stats = act.router().dispatch("/stats/daily", "GET", null);
                    before = stats == null ? null : stats.optJSONArray("ach");
                } catch (Throwable ignore) { before = null; }
                JSONObject b = new JSONObject();
                b.put("mode", mode);
                b.put("layer", layer);
                b.put("win", win);
                b.put("max_combo", maxCombo);
                b.put("correct", correct);
                b.put("cards", act.runCards);   // GAP-05：本局选卡数（成就 cards5/cards10 计数）
                JSONObject res = act.router().dispatch("/progress/finish", "POST", b);
                // 优先等价字段 new_ach（后端若显式回传）；否则以全量 ach 与基线 diff
                JSONArray after = null;
                try {
                    after = res == null ? null : res.optJSONArray("new_ach");
                    if (after == null) after = res == null ? null : res.optJSONArray("ach");
                } catch (Throwable ignore) { after = null; }
                final JSONArray fafter = after;
                final JSONArray fbefore = before;
                final java.util.List<String> fresh = new java.util.ArrayList<>();
                // 终期微修：快照基线（dispatch 前 /stats/daily 的 ach）为 null 时无法 diff——
                // 若仍进入循环，fbefore==null 会把 finish 响应里的全量 ach 全部误判为本局新成就，
                // 弹出错误的「成就解锁」toast；故基线缺失时跳过 diff、不弹 toast。
                if (fafter != null && fbefore != null) {
                    for (int i = 0; i < fafter.length(); i++) {
                        String id = fafter.optString(i, "");
                        if (id.isEmpty()) continue;
                        boolean known = false;
                        if (fbefore != null) {
                            for (int j = 0; j < fbefore.length(); j++) {
                                if (id.equals(fbefore.optString(j))) { known = true; break; }
                            }
                        }
                        if (!known) fresh.add(id);
                    }
                }
                if (!fresh.isEmpty()) {
                    act.main().post(() -> {
                        try {
                            StringBuilder sb = new StringBuilder("🏆 成就解锁：");
                            for (int i = 0; i < fresh.size(); i++) {
                                if (i > 0) sb.append("、");
                                sb.append(MainActivity.achName(fresh.get(i)));
                            }
                            android.widget.Toast.makeText(getContext(), sb.toString(),
                                    android.widget.Toast.LENGTH_LONG).show();
                            Sfx.play(getContext(), Sfx.EPIC);   // R3：复用现有 EPIC 枚举，不新增资源
                        } catch (Throwable ignore) {}
                    });
                }
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
        lvinfo.setOrientation(VERTICAL);
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

        // statgrid（V7.0：2×2 统计格）
        LinearLayout row1 = new LinearLayout(getContext());
        row1.setOrientation(HORIZONTAL);
        addStat(row1, questions + "", "答题数", true);
        addStat(row1, acc + "%", "正确率", false);
        body.addView(row1);
        body.addView(spacer(8));
        LinearLayout row2 = new LinearLayout(getContext());
        row2.setOrientation(HORIZONTAL);
        addStat(row2, maxCombo + "", "最高连击", true);
        if (!endless) addStat(row2, String.valueOf(act.runNewWords.size()), "新学词", false);   // GAP-14：与下方 chips 同一数据源
        if (endless) addStat(row2, kills + "", "击败敌人", true);
        body.addView(row2);
        body.addView(spacer(10));

        // 词卡（新学/错词 pill chips）
        wordCardHost = new LinearLayout(getContext());
        wordCardHost.setOrientation(VERTICAL);
        wordCardHost.setPadding(dp(14), dp(12), dp(14), dp(12));
        wordCardHost.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        wordCardHost.addView(Ui.text(getContext(), "本次新学的词", 13, Ui.TXT, true));
        wordCardHost.addView(chipsRow(act.runNewWords, Ui.CYAN));
        wordCardHost.addView(spacer(8));
        wordCardHost.addView(Ui.text(getContext(), "仍需复习的错词", 13, Ui.TXT, true));
        wordCardHost.addView(chipsRow(act.runWrongWords, Ui.RED));
        // R3：有错词时提供直达错词本复习入口（MainActivity 路由 WRONGBOOK 已可达，直达）
        if (!act.runWrongWords.isEmpty()) {
            TextView toWrong = Ui.text(getContext(), "📕 去错词本复习", 13, Ui.CYAN, true);
            toWrong.setGravity(Gravity.CENTER);
            toWrong.setPadding(dp(10), dp(10), dp(10), dp(10));
            toWrong.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.CYAN));
            Ui.pressable(toWrong);
            toWrong.setOnClickListener(v -> act.show(MainActivity.Screen.WRONGBOOK));
            wordCardHost.addView(toWrong);
            wordCardHost.addView(spacer(4));
        }
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
        Ui.ctaGlow(againBtn);
        againBtn.setOnClickListener(v -> act.show(MainActivity.Screen.BATTLE));
        body.addView(againBtn);
        body.addView(spacer(8));
        TextView home = Ui.text(getContext(), "返回主页", 13, Ui.MUT, true);
        home.setGravity(Gravity.CENTER);
        home.setPadding(dp(10), dp(10), dp(10), dp(10));
        home.setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
        Ui.pressable(home);
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
            bigSub.setText(win ? "你击败了词魇·深渊领主，五层远征通关！" : "别灰心，错词已经全部保留，会在复习中再出现。");
        }
        fillLevelCard(badge, ll, barl);

        // V11.3 逐项入场
        Ui.entrance(face, 60);
        Ui.entrance(lv, 200);
        Ui.entrance(wordCardHost, 330);
        Ui.entrance(againBtn, 460);
    }

    private void addStat(LinearLayout row, String v, String l, boolean count) {
        LinearLayout cell = new LinearLayout(getContext());
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(6), dp(12), dp(6), dp(12));
        cell.setBackground(Ui.card(12, Ui.BG1, 1, Ui.LINE));
        TextView vv = Ui.text(getContext(), v, 20, Ui.TXT, true);
        TextView ll = Ui.text(getContext(), l, 10.5f, Ui.MUT, false);
        cell.addView(vv);
        cell.addView(ll);
        row.addView(cell, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(spacer(6));
        if (count) {
            int target = 0;
            try { target = Integer.parseInt(v); } catch (Throwable ignore) {}
            countUp(vv, target, 200);
        }
    }

    /** 数字计数动画。 */
    private void countUp(TextView t, int target, long delay) {
        ValueAnimator a = ValueAnimator.ofInt(0, target);
        a.setStartDelay(delay);
        a.setDuration(650);
        a.addUpdateListener(v -> t.setText(String.valueOf(v.getAnimatedValue())));
        a.start();
    }

    /** 胜利彩带：顶部下落的彩色纸条，下落 + 旋转 + 淡出（V11.3）。 */
    private void confetti() {
        try {
            int[] colors = {0xFFFBBF24, 0xFF38BDF8, 0xFFC084FC, 0xFF34D399, 0xFFDB2777};
            int w = getWidth();
            for (int i = 0; i < 34; i++) {
                View p = new View(getContext());
                int cw = dp(5 + (float) Math.random() * 5);
                int ch = dp(8 + (float) Math.random() * 9);
                p.setBackground(Ui.card(1, colors[(int) (Math.random() * colors.length)], 0, 0));
                LayoutParams lp = new LayoutParams(cw, ch);
                lp.leftMargin = (int) (Math.random() * Math.max(1, w));
                lp.topMargin = -dp(20);
                addView(p, lp);
                float fall = getHeight() + dp(40);
                int dur = 1500 + (int) (Math.random() * 1300);
                p.animate().translationY(fall).rotation(360 + (float) Math.random() * 360)
                        .translationX((float) (Math.random() * dp(40) - dp(20)))
                        .alpha(0.9f).setDuration(dur)
                        .withEndAction(() -> { try { removeView(p); } catch (Throwable ignore) {} }).start();
            }
        } catch (Throwable ignore) {}
    }

    private TextView chipsOf(String text, int color) {
        TextView t = Ui.text(getContext(), text, 12, color, false);
        t.setPadding(0, dp(4), 0, 0);
        return t;
    }

    /** V7.0 词 pill chips：一排圆角描边小胶囊，空则显示「无」。 */
    private LinearLayout chipsRow(java.util.List<String> words, int color) {
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(HORIZONTAL);
        wrap.setPadding(0, dp(5), 0, dp(2));
        if (words == null || words.isEmpty()) {
            wrap.addView(Ui.text(getContext(), "无", 12, Ui.MUT, false));
            return wrap;
        }
        for (int i = 0; i < words.size(); i++) {
            TextView chip = Ui.text(getContext(), words.get(i), 12, color, true);
            chip.setPadding(dp(10), dp(4), dp(10), dp(4));
            chip.setBackground(Ui.pill(Ui.BG_PILL, color));
            wrap.addView(chip);
            if (i < words.size() - 1) wrap.addView(Ui.spacer(getContext(), 6));
        }
        return wrap;
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

    @Override
    protected void onDetachedFromWindow() {
        if (getBackground() instanceof AmbientBackground) ((AmbientBackground) getBackground()).stop();
        super.onDetachedFromWindow();
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

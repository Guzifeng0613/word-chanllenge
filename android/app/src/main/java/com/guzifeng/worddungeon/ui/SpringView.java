package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Map;

/**
 * 复习之泉（V7.0 startSpring 行 2430；截图 10-spring）：
 * 每层固定节点 —— 先回 30 HP，再把本层错词（顽固>易错>生疏）做成「记得/不记得」复习，
 * 答对每词 +2 HP。纯前端展示层；错词列表由 BattleView 按本层错题注入。
 */
public class SpringView extends FrameLayout {

    public interface OnSpring {
        void onHeal(int newHp);
        void onDone();
    }

    private final Map<String, String[]> words;   // word → [pos, mean]
    private final OnSpring cb;
    private final LinearLayout col;
    private int hp;
    private int idx = 0;

    public SpringView(Context c, Map<String, String[]> layerWrong, int curHp, OnSpring callback) {
        super(c);
        this.words = layerWrong;
        this.cb = callback;
        // 泉水先回 30 HP
        this.hp = Math.max(0, Math.min(100, curHp + 30));
        try { cb.onHeal(this.hp); } catch (Throwable ignore) {}
        setClickable(true);
        setBackgroundColor(0xDD08111F);

        ScrollView sv = new ScrollView(c);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(col);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        render();
    }

    private void render() {
        col.removeAllViews();
        // 顶部泉水卡
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER);
        top.setPadding(dp(16), dp(18), dp(16), dp(18));
        top.setBackground(Ui.card(16, Ui.CARD, 1, Ui.GREEN));
        TextView title = Ui.text(getContext(), "复习之泉", 19, Ui.GREEN, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title);
        TextView dia = Ui.text(getContext(), "◆", 30, 0xFFEAF2FC, false);
        dia.setGravity(Gravity.CENTER);
        dia.setPadding(0, dp(10), 0, dp(10));
        top.addView(dia);
        TextView sub = Ui.text(getContext(), "泉水恢复了 30 HP，回顾一下刚才错过的词", 12.5f, Ui.MUT, false);
        sub.setGravity(Gravity.CENTER);
        top.addView(sub);
        col.addView(top);
        col.addView(spacer(14));

        if (words == null || words.isEmpty()) {
            TextView go = Ui.text(getContext(), "继续 ›", 15, 0xFF081326, true);
            go.setGravity(Gravity.CENTER);
            go.setPadding(dp(14), dp(13), dp(14), dp(13));
            go.setBackground(Ui.gradient(12, 0xFFFCD34D, 0xFFF59E0B, 0));
            go.setOnClickListener(v -> done());
            col.addView(go);
            return;
        }

        String[] keys = words.keySet().toArray(new String[0]);
        if (idx >= keys.length) {
            TextView doneT = Ui.text(getContext(), "复习完成，泉水之力已灌注", 14, Ui.GREEN, true);
            doneT.setGravity(Gravity.CENTER);
            doneT.setPadding(0, dp(20), 0, dp(20));
            col.addView(doneT);
            TextView go = Ui.text(getContext(), "前往商店 ›", 15, 0xFF081326, true);
            go.setGravity(Gravity.CENTER);
            go.setPadding(dp(14), dp(13), dp(14), dp(13));
            go.setBackground(Ui.gradient(12, 0xFFFCD34D, 0xFFF59E0B, 0));
            go.setOnClickListener(v -> done());
            col.addView(go);
            return;
        }

        String word = keys[idx];
        String[] pm = words.get(word);
        String pos = pm == null ? "" : pm[0];
        String mean = pm == null ? "" : pm[1];

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));
        card.setBackground(Ui.card(16, Ui.CARD, 1, Ui.LINE));
        TextView w = Ui.text(getContext(), word, 24, Ui.TXT, true);
        w.setGravity(Gravity.CENTER);
        card.addView(w);
        if (!pos.isEmpty()) {
            TextView pt = Ui.posBadge(getContext(), pos);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-2, -2);
            pl.topMargin = dp(6);
            card.addView(pt, pl);
        }
        TextView mv = Ui.text(getContext(), mean, 14, Ui.MUT, false);
        mv.setGravity(Gravity.CENTER);
        mv.setPadding(0, dp(8), 0, dp(6));
        card.addView(mv);
        TextView tip = Ui.text(getContext(), "还记得这个词吗？（复习 " + (idx + 1) + "/" + keys.length + "）",
                11.5f, Ui.MUT, false);
        tip.setGravity(Gravity.CENTER);
        card.addView(tip);
        col.addView(card);
        col.addView(spacer(12));

        TextView rem = Ui.button(getContext(), "记得（+2 HP）", Ui.GREEN, Ui.BG0, false);
        TextView forget = Ui.button(getContext(), "不记得", Ui.CARD2, Ui.TXT, false);
        Ui.pressable(rem);
        Ui.pressable(forget);
        rem.setOnClickListener(v -> {
            hp = Math.max(0, Math.min(100, hp + 2));
            try { cb.onHeal(hp); } catch (Throwable ignore) {}
            Ui.vibrate(getContext(), 22);
            idx++;
            render();
        });
        forget.setOnClickListener(v -> { idx++; render(); });
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(rem, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(spacer(10));
        row.addView(forget, new LinearLayout.LayoutParams(0, -2, 1f));
        col.addView(row);
    }

    private void done() {
        try { cb.onDone(); } catch (Throwable ignore) {}
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

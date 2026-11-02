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
    private final int maxHp;   // V15.3：生命上限跟随祭坛体魄祝福（100+10×vigor）
    private int hp;
    private int idx = 0;

    public SpringView(Context c, Map<String, String[]> layerWrong, int curHp, int maxHp, OnSpring callback) {
        super(c);
        this.words = layerWrong;
        this.cb = callback;
        this.maxHp = maxHp;
        // 泉水先回 30 HP
        this.hp = Math.max(0, Math.min(maxHp, curHp + 30));
        try { cb.onHeal(this.hp); } catch (Throwable ignore) {}
        setClickable(true);
        setBackgroundColor(0xDD08111F);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);   // V12.7.11 复习之泉整体上下居中
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);   // V12.7.11 整列垂直居中；内容超出时正常滚动
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
            Ui.pressable(go);   // V15.3：继续按钮按压反馈
            go.setOnClickListener(v -> done());
            col.addView(go);
            return;
        }

        String[] keys = words.keySet().toArray(new String[0]);
        // GAP-11：按本层出错次数降序（≈顽固>易错>生疏）
        java.util.Arrays.sort(keys, (a, b) -> {
            int ca = parseCount(words.get(a));
            int cb = parseCount(words.get(b));
            return cb - ca;
        });
        if (idx >= keys.length) {
            TextView doneT = Ui.text(getContext(), "复习完成，泉水之力已灌注", 14, Ui.GREEN, true);
            doneT.setGravity(Gravity.CENTER);
            doneT.setPadding(0, dp(20), 0, dp(20));
            col.addView(doneT);
            TextView go = Ui.text(getContext(), "前往商店 ›", 15, 0xFF081326, true);
            go.setGravity(Gravity.CENTER);
            go.setPadding(dp(14), dp(13), dp(14), dp(13));
            go.setBackground(Ui.gradient(12, 0xFFFCD34D, 0xFFF59E0B, 0));
            Ui.pressable(go);   // V15.3：前往商店按钮按压反馈
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
            hp = Math.max(0, Math.min(maxHp, hp + 2));
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

    /** V15.9 M6：安全读取本层出错次数；缺字段或非数字时回退 1，避免比较器抛 NumberFormatException 中断排序。 */
    private static int parseCount(String[] p) {
        if (p == null || p.length <= 2) return 1;
        try {
            return Integer.parseInt(p[2]);
        } catch (Throwable t) {
            return 1;
        }
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

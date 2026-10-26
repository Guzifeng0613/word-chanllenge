package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/** 错词本：按错误次数/难度归档 + 逐个复习（记住/忘记） */
public class WrongbookView extends LinearLayout {

    private final MainActivity act;
    private final LinearLayout list;
    private JSONArray words;
    private int idx = 0;
    private int remembered = 0;

    public WrongbookView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(20), dp(16), dp(16));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(c, "📕 错词本", 20, Ui.GOLD, true);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView back = Ui.text(c, "← 返回", 14, Ui.CYAN, true);
        back.setPadding(dp(8), dp(6), dp(8), dp(6));
        back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        top.addView(back);
        addView(top);
        addView(spacer(10));

        ScrollView sv = new ScrollView(c);
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        refresh();
    }

    public void refresh() {
        list.removeAllViews();
        idx = 0;
        remembered = 0;
        try {
            JSONObject r = act.router().dispatch("/wrongbook", "GET", null);
            words = r.getJSONArray("words");
            if (words.length() == 0) {
                list.addView(Ui.text(getContext(), "暂无错词，继续战斗吧！", 15, Ui.MUT, false));
                return;
            }
            renderWord();
        } catch (Throwable e) {
            list.addView(Ui.text(getContext(), "加载失败：" + e.getMessage(), 14, Ui.RED, false));
        }
    }

    private void renderWord() {
        list.removeAllViews();
        try {
            JSONObject w = words.getJSONObject(idx);
            String word = w.optString("word", "");
            String pos = w.optString("pos", "");
            String mean = w.optString("mean", "");
            int wrong = w.optInt("wrong_count", 0);
            String grade = w.optString("grade", "");

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(12), dp(12), dp(12));
            card.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
            TextView wTxt = Ui.text(getContext(), word + "  " + (pos.isEmpty() ? "" : "[" + pos + "]"), 18, Ui.TXT, true);
            card.addView(wTxt);
            card.addView(Ui.text(getContext(), mean, 14, Ui.MUT, false));
            card.addView(spacer(6));
            card.addView(Ui.text(getContext(), "答错 " + wrong + " 次 · " + grade, 12, Ui.ORANGE, true));
            card.addView(spacer(10));
            TextView rem = Ui.button(getContext(), "✓ 记住了", Ui.GREEN, Ui.BG0, false);
            TextView forget = Ui.button(getContext(), "✗ 再复习", Ui.RED, Ui.BG0, false);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(rem, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(spacer(8));
            row.addView(forget, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(row);
            list.addView(card);

            rem.setOnClickListener(v -> reviewWord(word, true));
            forget.setOnClickListener(v -> reviewWord(word, false));

            TextView nav = Ui.text(getContext(), (idx + 1) + " / " + words.length() + " · 记住 " + remembered + " 个",
                    13, Ui.CYAN, false);
            nav.setGravity(Gravity.CENTER);
            list.addView(nav);
        } catch (Throwable e) {
            list.addView(Ui.text(getContext(), "渲染失败：" + e.getMessage(), 14, Ui.RED, false));
        }
    }

    private void reviewWord(String word, boolean rememberedFlag) {
        try {
            JSONObject body = new JSONObject();
            body.put("word", word);
            body.put("remembered", rememberedFlag);
            act.router().dispatch("/wrongbook/review", "POST", body);
            if (rememberedFlag) remembered++;
            idx++;
            if (idx >= words.length()) {
                list.removeAllViews();
                list.addView(Ui.text(getContext(), "复习完成！共记住 " + remembered + " 个", 16, Ui.GREEN, true));
                mainDelayedRefresh();
                return;
            }
            renderWord();
        } catch (Throwable e) {
            list.addView(Ui.text(getContext(), "复习失败：" + e.getMessage(), 14, Ui.RED, false));
        }
    }

    private void mainDelayedRefresh() {
        act.main().postDelayed(() -> {
            list.removeAllViews();
            refresh();
        }, 1200);
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

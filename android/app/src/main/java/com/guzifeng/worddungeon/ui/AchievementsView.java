package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONObject;

/** 成就页：基础统计（答题数 / 正确率 / 今日目标 / θ 水平） */
public class AchievementsView extends LinearLayout {

    private final MainActivity act;

    public AchievementsView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(20), dp(16), dp(16));

        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(c, "🏆 成就", 20, Ui.GOLD, true);
        top.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView back = Ui.text(c, "← 返回", 14, Ui.CYAN, true);
        back.setPadding(dp(8), dp(6), dp(8), dp(6));
        back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        top.addView(back);
        addView(top);
        addView(spacer(14));

        refresh();
    }

    private void refresh() {
        try {
            JSONObject st = act.router().dispatch("/stats/daily", "GET", null);
            int questions = st.optInt("questions", 0);
            int correct = st.optInt("correct", 0);
            int accuracy = st.optInt("accuracy", 0);
            int goal = st.optInt("daily_goal", 40);
            double theta = act.router().dispatch("/review/due", "GET", null).optDouble("theta", 1.5);

            addView(card("今日答题", questions + " 题", Ui.CYAN));
            addView(card("今日正确率", accuracy + "%", accuracy >= 85 ? Ui.GREEN : Ui.ORANGE));
            addView(card("今日目标", correct + " / " + goal + " 正确", Ui.GOLD));
            addView(card("θ 自适应水平", String.format("%.2f", theta), Ui.PURPLE));
            addView(spacer(10));
            addView(Ui.text(getContext(), "全对 85% 进入心流 · 连续打卡与完整成就系统迭代中", 12, Ui.MUT, false));
        } catch (Throwable e) {
            addView(Ui.text(getContext(), "统计加载失败：" + e.getMessage(), 14, Ui.RED, false));
        }
    }

    private View card(String k, String v, int color) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        TextView kb = Ui.text(getContext(), k, 15, Ui.MUT, false);
        box.addView(kb, new LinearLayout.LayoutParams(0, -2, 1f));
        box.addView(Ui.text(getContext(), v, 16, color, true));
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(box);
        addView(wrap);
        addView(spacer(8));
        return wrap;
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

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

/** 成就页（V7.0：成就墙 2 列网格，未解锁半透明） */
public class AchievementsView extends LinearLayout {

    private final MainActivity act;
    private final LinearLayout body;

    static final String[][] ACH_DEFS = {
            {"🏆", "初战告捷", "击败第 1 层 Boss", "boss1"},
            {"🏆", "穿透迷雾", "击败第 2 层 Boss", "boss2"},
            {"🏆", "征服深渊", "击败第 3 层 Boss", "boss3"},
            {"🌟", "远征通关", "完成一次完整远征", "first_win"},
            {"🔥", "连击高手", "单局连击达到 10", "combo10"},
            {"⚔️", "弹无虚发", "单局答对 50 题", "correct50"},
            {"📖", "初具规模", "一局收集 5 张词典卡", "cards5"},
            {"📚", "词典大成", "一局收集 10 张词典卡", "cards10"},
            {"🎓", "渐入佳境", "累计掌握 30 个词", "master30"},
            {"🎓", "词汇达人", "累计掌握 60 个词", "master60"},
            {"📅", "坚持不懈", "连续打卡 3 天", "streak3"},
            {"📅", "一周不辍", "连续打卡 7 天", "streak7"},
            {"🌀", "深渊行者", "无尽模式抵达第 5 层", "endless5"},
            {"💀", "深渊主宰", "无尽模式抵达第 10 层", "endless10"},
    };

    public AchievementsView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(body);
        addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        refresh();
    }

    public void refresh() {
        if (act.router() == null) return;
        body.removeAllViews();
        try {
            JSONObject st = act.router().dispatch("/stats/daily", "GET", null);
            JSONArray got = st.optJSONArray("ach");
            boolean[] has = new boolean[ACH_DEFS.length];
            for (int i = 0; i < ACH_DEFS.length; i++) {
                for (int j = 0; j < got.length(); j++) {
                    if (ACH_DEFS[i][3].equals(got.optString(j))) has[i] = true;
                }
            }
            int cnt = 0;
            for (boolean b : has) if (b) cnt++;

            // 顶栏（V7.0 规范：返回键在左上）
            LinearLayout top = new LinearLayout(getContext());
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView back = Ui.text(getContext(), "‹ 返回", 14, Ui.CYAN, true);
            back.setPadding(dp(8), dp(6), dp(8), dp(6));
            back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
            top.addView(back);
            TextView t = Ui.text(getContext(), "成就墙", 19, Ui.TXT, true);
            top.addView(t, lp(0, -2, 1f));
            body.addView(top);

            TextView hint = Ui.text(getContext(), "成就墙（" + cnt + "/" + ACH_DEFS.length + "）· 完成挑战解锁成就，记录你的成长", 11.5f, Ui.MUT, false);
            hint.setPadding(0, dp(6), 0, dp(8));
            body.addView(hint);

            for (int r = 0; r * 2 < ACH_DEFS.length; r++) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (int col = 0; col < 2; col++) {
                    int i = r * 2 + col;
                    if (i >= ACH_DEFS.length) break;
                    row.addView(cell(ACH_DEFS[i], has[i]), lp(0, -2, 1f));
                    if (col == 0) row.addView(spacer(8));
                }
                body.addView(row);
                body.addView(spacer(8));
            }

            TextView home = Ui.text(getContext(), "返回主页", 13, Ui.MUT, true);
            home.setGravity(Gravity.CENTER);
            home.setPadding(dp(10), dp(10), dp(10), dp(10));
            home.setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
            home.setClickable(true);
            home.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
            body.addView(home);
        } catch (Throwable e) {
            body.addView(Ui.text(getContext(), "成就加载失败：" + e.getMessage(), 14, Ui.RED, false));
        }
    }

    private LinearLayout cell(String[] def, boolean got) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(9), dp(9), dp(9), dp(9));
        c.setBackground(Ui.card(10, got ? Ui.BG1 : Ui.BG_PILL, 1, Ui.LINE));
        TextView ic = Ui.text(getContext(), def[0], 18, Ui.TXT, false);
        c.addView(ic);
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView nm = Ui.text(getContext(), def[1], 12.5f, got ? Ui.TXT : Ui.MUT, true);
        TextView ds = Ui.text(getContext(), def[2], 10.5f, Ui.MUT, false);
        tx.addView(nm);
        tx.addView(ds);
        LinearLayout.LayoutParams tl = lp(0, -2, 1f);
        tl.leftMargin = dp(8);
        tx.setLayoutParams(tl);
        c.addView(tx);
        if (!got) c.setAlpha(0.45f);
        return c;
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

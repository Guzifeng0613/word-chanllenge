package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 错词本（V7.0：顽固/易错/生疏 三档分组列表 + 错过地点标签 + 顽固优先复习） */
public class WrongbookView extends LinearLayout {

    private final MainActivity act;
    private final LinearLayout body;
    private final List<String> queue = new ArrayList<>();
    private int idx = 0;
    private int remembered = 0;

    public WrongbookView(Context c) {
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
        idx = 0;
        remembered = 0;
        queue.clear();
        try {
            JSONObject r = act.router().dispatch("/wrongbook", "GET", null);
            JSONArray words = r.getJSONArray("words");
            JSONObject summary = r.optJSONObject("summary");
            buildList(words, summary);
        } catch (Throwable e) {
            body.addView(Ui.text(getContext(), "加载失败：" + e.getMessage(), 14, Ui.RED, false));
        }
    }

    private void buildList(JSONArray words, JSONObject summary) {
        // 顶栏
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = Ui.text(getContext(), "错词本", 19, Ui.TXT, true);
        top.addView(t, lp(0, -2, 1f));
        TextView back = Ui.text(getContext(), "‹ 返回", 14, Ui.CYAN, true);
        back.setPadding(dp(8), dp(6), dp(8), dp(6));
        back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        top.addView(back);
        body.addView(top);

        TextView hint = Ui.text(getContext(), "按错误次数分级，顽固词反复出现，答对降级直至掌握", 11.5f, Ui.MUT, false);
        hint.setPadding(0, dp(6), 0, dp(8));
        body.addView(hint);

        if (words.length() == 0) {
            body.addView(spacer(6));
            TextView empty = Ui.text(getContext(), "没有错词，继续远征！", 15, Ui.MUT, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            empty.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
            body.addView(empty);
            return;
        }

        android.util.Log.d("WD", "wrongbook words.length=" + words.length());
        // 三档分组：顽固3 / 易错2 / 生疏1
        int[] gv = {3, 2, 1};
        int[] gc = {0xFFF87171, 0xFFFB923C, 0xFFFBBF24};
        String[] gn = {"顽固", "易错", "生疏"};
        for (int gi = 0; gi < gv.length; gi++) {
            int grade = gv[gi];
            int color = gc[gi];
            String name = gn[gi];
            int cnt = 0;
            for (int i = 0; i < words.length(); i++) {
                if (words.optJSONObject(i).optInt("grade", 0) == grade) cnt++;
            }
            if (cnt == 0) continue;
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(10));
            card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
            TextView h = Ui.text(getContext(), name + "（" + cnt + "）", 14, color, true);
            card.addView(h);
            for (int i = 0; i < words.length(); i++) {
                JSONObject w = words.optJSONObject(i);
                if (w.optInt("grade", 0) != grade) continue;
                android.util.Log.d("WD", "render grade=" + grade + " word=" + w.optString("word","?"));
                card.addView(wordRow(w, color));
            }
            body.addView(card);
            body.addView(spacer(10));
        }

        // 开始复习（顽固优先）
        TextView start = Ui.text(getContext(), "开始复习（顽固优先）", 15, 0xFF081326, true);
        start.setGravity(Gravity.CENTER);
        start.setPadding(dp(14), dp(13), dp(14), dp(13));
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFFCD34D, 0xFFF59E0B});
        gd.setCornerRadius(dp(13));
        start.setBackground(gd);
        start.setOnClickListener(v -> startReview(words));
        body.addView(start);
        body.addView(spacer(8));
        TextView home = Ui.text(getContext(), "返回主页", 13, Ui.MUT, true);
        home.setGravity(Gravity.CENTER);
        home.setPadding(dp(10), dp(10), dp(10), dp(10));
        home.setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
        home.setClickable(true);
        home.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        body.addView(home);
    }

    private LinearLayout wordRow(JSONObject w, int color) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView word = Ui.text(getContext(), w.optString("word", ""), 15, Ui.TXT, true);
        String pos = w.optString("pos", "");
        if (!pos.isEmpty()) {
            TextView pt = Ui.text(getContext(), pos, 11, Ui.GOLD, true);
            pt.setPadding(dp(5), 0, dp(5), 0);
            top.addView(word);
            top.addView(pt);
        } else {
            top.addView(word);
        }
        top.addView(Ui.text(getContext(), "", 0, 0, false), lp(0, -2, 1f));
        TextView cnt = Ui.text(getContext(), "×" + w.optInt("wrong_count", 1), 13, color, true);
        top.addView(cnt);
        row.addView(top);
        TextView mean = Ui.text(getContext(), w.optString("mean", ""), 12.5f, Ui.MUT, false);
        row.addView(mean);
        // 错过地点标签（wrong_by）
        JSONArray wb = w.optJSONArray("wrong_by");
        if (wb != null && wb.length() > 0) {
            LinearLayout tags = new LinearLayout(getContext());
            tags.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < wb.length(); i++) {
                tags.addView(tagOf(wb.optString(i)));
                if (i < wb.length() - 1) tags.addView(spacer(4));
            }
            row.addView(tags);
        }
        row.addView(Ui.divider(getContext()), lp(-1, -2, 0));
        return row;
    }

    private TextView tagOf(String tag) {
        // "level/qtype:n"
        String label = tag;
        try {
            String[] p = tag.split("[/:]");
            String level = p.length > 0 ? p[0] : "";
            String qt = p.length > 1 ? p[1] : "";
            String n = p.length > 2 ? p[2] : "1";
            String[] lmap = {"normal", "hard", "hell"};
            String[] lname = {"普通", "困难", "地狱"};
            String[] tmap = {"judge", "en2cn", "cn2en", "spell"};
            String[] tname = {"判断", "选义", "选词", "拼写"};
            String ls = level;
            String ts = qt;
            for (int i = 0; i < lmap.length; i++) if (lmap[i].equals(level)) ls = lname[i];
            for (int i = 0; i < tmap.length; i++) if (tmap[i].equals(qt)) ts = tname[i];
            label = ls + ts + "×" + n;
        } catch (Exception ignore) {}
        TextView t = Ui.text(getContext(), label, 10, Ui.MUT, true);
        t.setPadding(dp(7), dp(3), dp(7), dp(3));
        t.setBackground(Ui.pill(Ui.BG_PILL, Ui.LINE));
        return t;
    }

    private void startReview(JSONArray words) {
        queue.clear();
        // 顽固优先：grade 3 → 2 → 1
        for (int g = 3; g >= 1; g--) {
            for (int i = 0; i < words.length(); i++) {
                JSONObject w = words.optJSONObject(i);
                if (w.optInt("grade", 0) == g) queue.add(w.optString("word", ""));
            }
        }
        idx = 0;
        remembered = 0;
        renderReview();
    }

    private void renderReview() {
        body.removeAllViews();
        if (idx >= queue.size()) {
            TextView done = Ui.text(getContext(), "复习完成！共记住 " + remembered + " 个", 16, Ui.GREEN, true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(0, dp(30), 0, dp(30));
            body.addView(done);
            TextView go = Ui.text(getContext(), "返回错词本", 13, Ui.MUT, true);
            go.setGravity(Gravity.CENTER);
            go.setPadding(dp(10), dp(10), dp(10), dp(10));
            go.setBackground(Ui.card(12, Ui.CARD2, 1, Ui.LINE));
            go.setClickable(true);
            go.setOnClickListener(v -> refresh());
            body.addView(go);
            return;
        }
        final String word = queue.get(idx);
        try {
            JSONObject wb = act.router().dispatch("/wrongbook", "GET", null);
            JSONArray words = wb.getJSONArray("words");
            String pos = "", mean = "";
            int wrong = 0;
            for (int i = 0; i < words.length(); i++) {
                if (word.equals(words.optJSONObject(i).optString("word"))) {
                    pos = words.optJSONObject(i).optString("pos", "");
                    mean = words.optJSONObject(i).optString("mean", "");
                    wrong = words.optJSONObject(i).optInt("wrong_count", 1);
                    break;
                }
            }
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
            TextView wt = Ui.text(getContext(), word + (pos.isEmpty() ? "" : "  [" + pos + "]"), 20, Ui.TXT, true);
            card.addView(wt);
            TextView mv = Ui.text(getContext(), mean, 15, Ui.MUT, false);
            mv.setPadding(0, dp(6), 0, 0);
            card.addView(mv);
            TextView info = Ui.text(getContext(), "答错 " + wrong + " 次", 12, Ui.ORANGE, true);
            info.setPadding(0, dp(8), 0, dp(12));
            card.addView(info);
            TextView rem = Ui.button(getContext(), "✓ 记住了", Ui.GREEN, Ui.BG0, false);
            TextView forget = Ui.button(getContext(), "✗ 再复习", Ui.RED, Ui.BG0, false);
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(rem, lp(0, -2, 1f));
            row.addView(spacer(8));
            row.addView(forget, lp(0, -2, 1f));
            card.addView(row);
            body.addView(card);
            TextView nav = Ui.text(getContext(), (idx + 1) + " / " + queue.size() + " · 记住 " + remembered + " 个", 13, Ui.CYAN, false);
            nav.setGravity(Gravity.CENTER);
            nav.setPadding(0, dp(10), 0, 0);
            body.addView(nav);
            rem.setOnClickListener(v -> reviewWord(word, true));
            forget.setOnClickListener(v -> reviewWord(word, false));
        } catch (Throwable e) {
            body.addView(Ui.text(getContext(), "渲染失败：" + e.getMessage(), 14, Ui.RED, false));
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
            renderReview();
        } catch (Throwable e) {
            body.addView(Ui.text(getContext(), "复习失败：" + e.getMessage(), 14, Ui.RED, false));
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

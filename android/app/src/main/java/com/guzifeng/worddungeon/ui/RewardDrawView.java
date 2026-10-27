package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 翻牌抽卡（V7.0 viewChoice / autoReveal；GAP-01 接真实后端 /card/draw）：
 *  - 战斗胜利后翻 3 张词典卡，槽位由后端 CardCatalog.draw 返回（25 卡目录 + pity 保底）
 *  - 自动按稀有度从低到高依次翻开，史诗压轴放金光
 *  - 每张卡「选择此卡」；底部「刷新一组 · 25 词魂」（真实扣词魂，BattleView 走 /shop/buy reroll）/「放弃（不拿卡）」
 * 槽位 JSON 形如 {slots:[{rare, card:{id,name,rarity,effect}}], pity, epicPity}。
 */
public class RewardDrawView extends FrameLayout {

    /** chosen=null 表示放弃不拿卡；reroll 请求由 BattleView 走真实购买后重建本浮层。 */
    public interface OnReward {
        void onReward(JSONObject card);
        void onReroll();
    }

    private final OnReward callback;
    private final List<JSONObject> slots = new ArrayList<>();
    private TextView epicBanner, refreshBtn;
    private LinearLayout row;
    private boolean done = false;
    private boolean refreshed = false;

    public RewardDrawView(Context c, JSONObject drawRes, OnReward cb) {
        super(c);
        this.callback = cb;
        setClickable(true);
        setBackgroundColor(0xDD08111F);

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(16), dp(18), dp(16), dp(16));
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView title = Ui.text(c, "战斗胜利", 19, Ui.GOLD, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView sub = Ui.text(c, "翻开词典卡，选择一张加入你的词典页", 11.5f, Ui.MUT, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(10));
        panel.addView(sub);

        epicBanner = Ui.text(c, "史诗降临！", 13, Ui.GOLD, true);
        epicBanner.setGravity(Gravity.CENTER);
        epicBanner.setPadding(dp(12), dp(7), dp(12), dp(7));
        epicBanner.setBackground(Ui.card(999, 0x33FBBF24, 1, Ui.GOLD));
        epicBanner.setVisibility(GONE);
        panel.addView(epicBanner);
        panel.addView(Ui.spacer(c, 8));

        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        panel.addView(row);

        loadSlots(drawRes);

        panel.addView(Ui.spacer(c, 16));
        refreshBtn = Ui.text(c, "刷新一组 · 25 词魂", 13, Ui.MUT, true);
        refreshBtn.setGravity(Gravity.CENTER);
        refreshBtn.setPadding(dp(14), dp(11), dp(14), dp(11));
        refreshBtn.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        Ui.pressable(refreshBtn);
        refreshBtn.setOnClickListener(v -> {
            if (done || refreshed) return;
            refreshed = true;
            callback.onReroll();   // BattleView 真实扣 25 词魂并重抽
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panel.addView(refreshBtn, rp);
        panel.addView(Ui.spacer(c, 8));

        TextView abandon = Ui.text(c, "放弃（不拿卡）", 13, Ui.MUT, true);
        abandon.setGravity(Gravity.CENTER);
        abandon.setPadding(dp(14), dp(11), dp(14), dp(11));
        abandon.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        Ui.pressable(abandon);
        abandon.setOnClickListener(v -> { if (!done) { done = true; callback.onReward(null); } });
        panel.addView(abandon, rp);
    }

    /** V12.5 稀有度光柱：r1 蓝/r2 紫，从底部向上展开 1.5s。 */
    private void rarityRay(int rare) {
        try {
            View ray = new View(getContext());
            int w = dp(rare >= 2 ? 90 : 70);
            int color = rare >= 2 ? 0xCCC084FC : 0xAA38BDF8;
            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable(
                            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                            new int[]{color, 0x00000000});
            ray.setBackground(g);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w,
                    FrameLayout.LayoutParams.MATCH_PARENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            addView(ray, lp);
            ray.setScaleY(0f); ray.setAlpha(0f);
            ray.setPivotY(getHeight());
            ray.animate().scaleY(1f).alpha(1f).setDuration(300)
                    .withEndAction(() -> ray.animate().alpha(0f).setDuration(1000)
                            .withEndAction(() -> { try { removeView(ray); } catch (Throwable ignore) {} }).start()).start();
        } catch (Throwable ignore) {}
    }

    /** 解析后端槽位并按稀有度从低到高依次自动翻开。 */
    private void loadSlots(JSONObject drawRes) {
        slots.clear();
        row.removeAllViews();
        if (drawRes != null) {
            JSONArray arr = drawRes.optJSONArray("slots");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.optJSONObject(i);
                    if (s != null) slots.add(s);
                }
            }
        }
        if (slots.isEmpty()) {   // 后端未就绪：回退本地占位，不阻断流程
            slots.add(slotOf(0, "速记", "3 秒内答对的伤害额外 +30%"));
            slots.add(slotOf(1, "灵光乍现", "每题 15% 概率暴击，造成双倍伤害"));
            slots.add(slotOf(2, "致命一击", "暴击伤害 ×3，并获得 10% 暴击率"));
        }
        List<JSONObject> sorted = new ArrayList<>(slots);
        Collections.sort(sorted, (a, b) -> a.optInt("rare", 0) - b.optInt("rare", 0));
        for (int i = 0; i < sorted.size(); i++) {
            CardView card = new CardView(getContext(), sorted.get(i));
            row.addView(card.root);
            if (i < sorted.size() - 1) row.addView(Ui.spacer(getContext(), 8));
            final int idx = i;
            row.postDelayed(() -> {
                flip(card);
                if (card.rare() >= 2) {
                    epicBanner.setVisibility(VISIBLE);
                    Ui.vibrate(getContext(), new long[]{0, 40, 30, 60});
                }
            }, 260L + idx * 320L);
        }
    }

    private JSONObject slotOf(int rare, String name, String effect) {
        JSONObject s = new JSONObject();
        try {
            s.put("rare", rare);
            JSONObject c = new JSONObject();
            c.put("id", "local_" + name);
            c.put("name", name);
            c.put("rarity", rare);
            c.put("effect", effect);
            s.put("card", c);
        } catch (Exception ignore) {}
        return s;
    }

    /** 单张牌：卡背 ↔ 正面（稀有度 pill + 名 + 效果 + 选择按钮）。 */
    private final class CardView {
        final LinearLayout root;
        final JSONObject slot;
        boolean flipped = false;

        CardView(Context c, JSONObject s) {
            this.slot = s;
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setLayoutParams(new LinearLayout.LayoutParams(dp(104), dp(150)));
            root.setBackground(Ui.card(12, Ui.BG_PILL, 2, Ui.GOLD));
            TextView q = Ui.text(c, "❔", 26, Ui.GOLD, true);
            q.setGravity(Gravity.CENTER);
            root.addView(q);
            TextView ql = Ui.text(c, "词典卡", 10, Ui.MUT, true);
            ql.setGravity(Gravity.CENTER);
            root.addView(ql);
        }

        int rare() { return slot.optInt("rare", 0); }

        void reveal() {
            root.removeAllViews();
            int rare = rare();
            int color = rare >= 2 ? Ui.PURPLE : rare == 1 ? Ui.GOLD : Ui.CYAN;
            if (rare >= 1) rarityRay(rare);   // V12.5 稀有度光柱
            root.setBackground(Ui.card(12, Ui.BG1, rare >= 2 ? 2 : 1, rare >= 1 ? Ui.GOLD : Ui.LINE));
            TextView pill = Ui.text(getContext(), rarityName(rare), 10, color, true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(8), dp(2), dp(8), dp(2));
            pill.setBackground(Ui.pill(Ui.BG_PILL, color));
            root.addView(pill);
            JSONObject c = slot.optJSONObject("card");
            String name = c != null ? c.optString("name", "词典卡") : "词典卡";
            String eff = c != null ? c.optString("effect", "") : "";
            TextView ic = Ui.text(getContext(), rare >= 2 ? "✨" : rare == 1 ? "⚡" : "📜", 24, color, false);
            ic.setGravity(Gravity.CENTER);
            ic.setPadding(0, dp(4), 0, dp(2));
            root.addView(ic);
            TextView tt = Ui.text(getContext(), name, 11, color, true);
            tt.setGravity(Gravity.CENTER);
            tt.setPadding(dp(3), dp(2), dp(3), dp(2));
            root.addView(tt);
            TextView dd = Ui.text(getContext(), eff, 8.5f, Ui.TXT, false);
            dd.setGravity(Gravity.CENTER);
            dd.setMaxLines(4);
            root.addView(dd);
            TextView pick = Ui.text(getContext(), "选择此卡", 10.5f, rare >= 2 ? 0xFF081326 : Ui.BG0, true);
            pick.setGravity(Gravity.CENTER);
            pick.setPadding(dp(8), dp(6), dp(8), dp(6));
            pick.setBackground(Ui.gradient(999, 0xFF38BDF8, 0xFF3B82F6, 0));
            if (rare >= 2) pick.setBackground(Ui.gradient(999, 0xFFFCD34D, 0xFFF59E0B, 0));
            Ui.pressable(pick);
            pick.setOnClickListener(v -> {
                if (done) return;
                done = true;
                callback.onReward(c);
            });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pl.topMargin = dp(6);
            root.addView(pick, pl);
            if (rare >= 2) {
                Sfx.play(getContext(), Sfx.EPIC);   // V11.8 史诗揭晓明亮琶音
                root.setAlpha(0f);
                root.animate().alpha(1f).scaleX(1.05f).scaleY(1.05f).setDuration(320)
                        .withEndAction(() -> root.animate().scaleX(1f).scaleY(1f).setDuration(220).start()).start();
            }
        }
    }

    private String rarityName(int r) {
        return r >= 2 ? "史诗" : r == 1 ? "稀有" : "普通";
    }

    private void flip(CardView card) {
        if (card.flipped) return;
        card.flipped = true;
        Sfx.play(getContext(), Sfx.FLIP);   // V11.8 翻牌滑音
        card.root.animate().scaleX(0f).setDuration(160).withEndAction(() -> {
            card.reveal();
            card.root.animate().scaleX(1f).setDuration(180).start();
        }).start();
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
}

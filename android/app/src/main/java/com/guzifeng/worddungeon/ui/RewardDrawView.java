package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 翻牌抽卡（V7.0 viewChoice 行 2993 / autoReveal 行 3051；截图 08-choice-flip）：
 *  - 战斗胜利后翻 3 张词典卡（普通/稀有/史诗）
 *  - 自动按稀有度从低到高依次翻开，史诗压轴放金光
 *  - 每张卡「选择此卡」；底部「刷新一组·25 词魂」（每局限 1 次）/「放弃（不拿卡）」
 * 奖励内容池与三级稀有度数值由后端（Agent3）提供；此处展示池保持与 V11.3 一致。
 */
public class RewardDrawView extends FrameLayout {

    /** 一条奖励。 */
    public static final class Reward {
        public final int rarity;     // 1 普通 / 2 稀有 / 3 史诗
        public final String kind;    // heal / xp / damage / time
        public final int amount;
        public final String icon, title, desc;
        public final int color;

        Reward(int rarity, String kind, int amount, String icon, String title, String desc, int color) {
            this.rarity = rarity; this.kind = kind; this.amount = amount;
            this.icon = icon; this.title = title; this.desc = desc; this.color = color;
        }
    }

    /** chosen=null 表示放弃不拿卡。 */
    public interface OnReward {
        void onReward(Reward r);
    }

    private final List<Card> cards = new ArrayList<>();
    private final OnReward callback;
    private TextView epicBanner, refreshBtn;
    private boolean done = false;
    private boolean refreshed = false;
    private TextView soulHint;

    public RewardDrawView(Context c, OnReward cb) {
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
        soulHint = Ui.text(c, "翻开词典卡，选择一张加入你的词典页", 11.5f, Ui.MUT, false);
        soulHint.setGravity(Gravity.CENTER);
        soulHint.setPadding(0, dp(4), 0, dp(10));
        panel.addView(soulHint);

        epicBanner = Ui.text(c, "史诗降临！", 13, Ui.GOLD, true);
        epicBanner.setGravity(Gravity.CENTER);
        epicBanner.setPadding(dp(12), dp(7), dp(12), dp(7));
        epicBanner.setBackground(Ui.card(999, 0x33FBBF24, 1, Ui.GOLD));
        epicBanner.setVisibility(GONE);
        panel.addView(epicBanner);
        panel.addView(Ui.spacer(c, 8));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        loadPool(row);
        panel.addView(row);

        panel.addView(Ui.spacer(c, 16));
        refreshBtn = Ui.text(c, "刷新一组 · 25 词魂", 13, Ui.MUT, true);
        refreshBtn.setGravity(Gravity.CENTER);
        refreshBtn.setPadding(dp(14), dp(11), dp(14), dp(11));
        refreshBtn.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        Ui.pressable(refreshBtn);
        refreshBtn.setOnClickListener(v -> {
            if (done || refreshed) return;
            refreshed = true;
            cards.clear();
            ((LinearLayout) row).removeAllViews();
            loadPool(row);
            epicBanner.setVisibility(GONE);
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

    /** 抽 3 张池并按稀有度从低到高排序，依次自动翻开。 */
    private void loadPool(LinearLayout row) {
        Reward[] pool = rollPool();
        List<Reward> sorted = new ArrayList<>();
        for (Reward r : pool) sorted.add(r);
        Collections.sort(sorted, (a, b) -> a.rarity - b.rarity);
        boolean hasEpic = false;
        for (int i = 0; i < sorted.size(); i++) {
            Card card = new Card(getContext(), sorted.get(i));
            cards.add(card);
            row.addView(card.root);
            if (i < sorted.size() - 1) row.addView(Ui.spacer(getContext(), 8));
            final int idx = i;
            row.postDelayed(() -> {
                flip(card);
                if (card.reward.rarity >= 3) {
                    epicBanner.setVisibility(VISIBLE);
                    Ui.vibrate(getContext(), new long[]{0, 40, 30, 60});
                }
            }, 260L + idx * 320L);
        }
    }

    /** 随机奖励池：保证至少 1 张稀有及以上；其余按概率。 */
    private Reward[] rollPool() {
        Reward[] pool = new Reward[3];
        boolean hasRare = false;
        for (int i = 0; i < 3; i++) {
            double r = Math.random();
            int rarity = r < 0.12 ? 3 : r < 0.45 ? 2 : 1;
            if (rarity >= 2) hasRare = true;
            pool[i] = randomReward(rarity);
        }
        if (!hasRare) pool[(int) (Math.random() * 3)] = randomReward(2);
        return pool;
    }

    private Reward randomReward(int rarity) {
        if (rarity == 3) {
            int pick = (int) (Math.random() * 3);
            if (pick == 0) return new Reward(3, "damage", 50, "🗡", "史诗·狂暴", "本场伤害 ×1.5", Ui.PURPLE);
            if (pick == 1) return new Reward(3, "heal", 100, "💖", "史诗·回春", "生命完全恢复", Ui.PURPLE);
            return new Reward(3, "xp", 50, "🌟", "史诗·顿悟", "立即获得 50 XP", Ui.PURPLE);
        }
        if (rarity == 2) {
            int pick = (int) (Math.random() * 3);
            if (pick == 0) return new Reward(2, "damage", 30, "⚔️", "稀有·锋锐", "本场伤害 ×1.3", Ui.GOLD);
            if (pick == 1) return new Reward(2, "heal", 25, "❤️", "稀有·疗愈", "恢复 25 点生命", Ui.GOLD);
            return new Reward(2, "xp", 25, "✨", "稀有·灵光", "立即获得 25 XP", Ui.GOLD);
        }
        int pick = (int) (Math.random() * 3);
        if (pick == 0) return new Reward(1, "heal", 15, "🩹", "普通·包扎", "恢复 15 点生命", Ui.CYAN);
        if (pick == 1) return new Reward(1, "xp", 10, "✴️", "普通·心得", "立即获得 10 XP", Ui.CYAN);
        return new Reward(1, "time", 2, "⏳", "普通·余裕", "下题时间 +2 秒", Ui.CYAN);
    }

    /** 单张牌：卡背 ↔ 正面（稀有度 pill + 名 + 描述 + 选择按钮）。 */
    private final class Card {
        final LinearLayout root;
        final Reward reward;
        boolean flipped = false;

        Card(Context c, Reward r) {
            this.reward = r;
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            int w = dp(104), h = dp(150);
            root.setLayoutParams(new LinearLayout.LayoutParams(w, h));
            root.setBackground(Ui.card(12, Ui.BG_PILL, 2, Ui.GOLD));
            TextView q = Ui.text(c, "❔", 26, Ui.GOLD, true);
            q.setGravity(Gravity.CENTER);
            root.addView(q);
            TextView ql = Ui.text(c, "词典卡", 10, Ui.MUT, true);
            ql.setGravity(Gravity.CENTER);
            root.addView(ql);
        }

        /** 翻开：填充正面内容。 */
        void reveal() {
            root.removeAllViews();
            int border = reward.rarity == 3 ? Ui.GOLD : reward.rarity == 2 ? Ui.GOLD : Ui.LINE;
            root.setBackground(Ui.card(12, Ui.BG1, reward.rarity == 3 ? 2 : 1, border));
            TextView pill = Ui.text(getContext(), rarityName(reward.rarity), 10, reward.color, true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(dp(8), dp(2), dp(8), dp(2));
            pill.setBackground(Ui.pill(Ui.BG_PILL, reward.color));
            root.addView(pill);
            TextView ic = Ui.text(getContext(), reward.icon, 24, reward.color, false);
            ic.setGravity(Gravity.CENTER);
            ic.setPadding(0, dp(4), 0, dp(2));
            root.addView(ic);
            TextView tt = Ui.text(getContext(), reward.title, 11, reward.color, true);
            tt.setGravity(Gravity.CENTER);
            tt.setPadding(dp(3), dp(2), dp(3), dp(2));
            root.addView(tt);
            TextView dd = Ui.text(getContext(), reward.desc, 8.5f, Ui.TXT, false);
            dd.setGravity(Gravity.CENTER);
            dd.setMaxLines(3);
            root.addView(dd);
            TextView pick = Ui.text(getContext(), "选择此卡", 10.5f, reward.rarity == 3 ? 0xFF081326 : Ui.BG0, true);
            pick.setGravity(Gravity.CENTER);
            pick.setPadding(dp(8), dp(6), dp(8), dp(6));
            pick.setBackground(Ui.gradient(999, 0xFF38BDF8, 0xFF3B82F6, 0));
            if (reward.rarity == 3) pick.setBackground(Ui.gradient(999, 0xFFFCD34D, 0xFFF59E0B, 0));
            Ui.pressable(pick);
            pick.setOnClickListener(v -> {
                if (done) return;
                done = true;
                callback.onReward(reward);
            });
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pl.topMargin = dp(6);
            root.addView(pick, pl);
            // 史诗金光
            if (reward.rarity >= 3) {
                root.setAlpha(0f);
                root.animate().alpha(1f).scaleX(1.05f).scaleY(1.05f).setDuration(320)
                        .withEndAction(() -> root.animate().scaleX(1f).scaleY(1f).setDuration(220).start()).start();
            }
        }
    }

    private String rarityName(int r) {
        return r >= 3 ? "史诗" : r == 2 ? "稀有" : "普通";
    }

    private void flip(Card card) {
        if (card.flipped) return;
        card.flipped = true;
        card.root.animate().scaleX(0f).setDuration(160).withEndAction(() -> {
            card.reveal();
            card.root.animate().scaleX(1f).setDuration(180).start();
        }).start();
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
}

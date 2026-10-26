package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static android.widget.LinearLayout.HORIZONTAL;
import static android.widget.LinearLayout.VERTICAL;

import java.util.ArrayList;
import java.util.List;

/**
 * 战利品翻牌抽卡（V11.3）：击杀精英 / 冠军 Boss 后的仪式覆盖层。
 *  - 3 张暗牌，玩家点 1 张翻开（水平翻转），其余 2 张随后自动翻开
 *  - 三级稀有度（普通青 / 稀有金 / 史诗紫），翻牌光效 + 确认按钮
 *  - 奖励：回血 / XP / 伤害增益 / 时间延长，由回调 apply 落回战斗
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

    public interface OnReward {
        void onReward(Reward r);
    }

    private final List<Card> cards = new ArrayList<>();
    private final OnReward callback;
    private boolean picked = false;
    private TextView confirmBtn;
    private Reward chosen;

    public RewardDrawView(Context c, OnReward cb) {
        super(c);
        this.callback = cb;
        setClickable(true);
        setBackgroundColor(0xDD08111F);

        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(16), dp(20), dp(16), dp(18));
        panel.setBackground(Ui.gradient(18, Ui.CARD, Ui.CARD2, 90));
        addView(panel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        TextView title = Ui.text(c, "✦ 战利品 ✦", 20, Ui.GOLD, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView sub = Ui.text(c, "选择一张卡牌，命运自有安排", 12, Ui.MUT, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(4), 0, dp(16));
        panel.addView(sub);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Reward[] pool = rollPool();
        for (int i = 0; i < 3; i++) {
            Card card = new Card(c, pool[i]);
            cards.add(card);
            row.addView(card.root);
            if (i < 2) {
                View gap = new View(c);
                gap.setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
                row.addView(gap);
            }
        }
        panel.addView(row);

        confirmBtn = Ui.text(c, "继续远征", 15, 0xFF081326, true);
        confirmBtn.setGravity(Gravity.CENTER);
        confirmBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
        confirmBtn.setBackground(Ui.gradient(12, 0xFFFCD34D, 0xFFF59E0B, 0));
        confirmBtn.setVisibility(GONE);
        confirmBtn.setOnClickListener(v -> {
            if (callback != null && chosen != null) callback.onReward(chosen);
        });
        LinearLayout.LayoutParams cb2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cb2.topMargin = dp(18);
        panel.addView(confirmBtn, cb2);
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

    /** 单张牌：卡背 ↔ 正面。 */
    private final class Card {
        final LinearLayout root;
        final Reward reward;
        final View back, front;
        boolean flipped = false;

        Card(Context c, Reward r) {
            this.reward = r;
            root = new LinearLayout(c);
            root.setOrientation(VERTICAL);
            root.setGravity(Gravity.CENTER);
            int w = dp(96), h = dp(132);
            root.setLayoutParams(new LinearLayout.LayoutParams(w, h));

            // 卡背
            back = new LinearLayout(c);
            ((LinearLayout) back).setOrientation(VERTICAL);
            ((LinearLayout) back).setGravity(Gravity.CENTER);
            back.setBackground(Ui.card(12, Ui.BG_PILL, 2, Ui.GOLD));
            TextView q = Ui.text(c, "❔", 30, Ui.GOLD, true);
            ((LinearLayout) back).addView(q);
            TextView ql = Ui.text(c, "战利品", 10, Ui.MUT, true);
            ((LinearLayout) back).addView(ql);

            // 正面
            front = new LinearLayout(c);
            ((LinearLayout) front).setOrientation(VERTICAL);
            ((LinearLayout) front).setGravity(Gravity.CENTER);
            front.setBackground(Ui.card(12, Ui.BG1, 2, r.color));
            front.setVisibility(GONE);
            TextView ic = Ui.text(c, r.icon, 28, r.color, false);
            ((LinearLayout) front).addView(ic);
            TextView tt = Ui.text(c, r.title, 11.5f, r.color, true);
            tt.setGravity(Gravity.CENTER);
            tt.setPadding(dp(4), dp(6), dp(4), 0);
            ((LinearLayout) front).addView(tt);
            TextView dd = Ui.text(c, r.desc, 9.5f, Ui.TXT, false);
            dd.setGravity(Gravity.CENTER);
            dd.setPadding(dp(4), dp(3), dp(4), 0);
            dd.setMaxLines(2);
            ((LinearLayout) front).addView(dd);

            root.addView(back);
            root.addView(front);
            Ui.pressable(root);
            root.setOnClickListener(v -> {
                if (picked) return;
                picked = true;
                chosen = reward;
                flip(this, true);
                // 其余两张随后自动翻开
                for (int i = 0; i < cards.size(); i++) {
                    final Card other = cards.get(i);
                    if (other != this) {
                        final int idx = i;
                        postDelayed(() -> flip(other, false), 300 + idx * 220L);
                    }
                }
                postDelayed(() -> {
                    confirmBtn.setVisibility(VISIBLE);
                    Ui.entrance(confirmBtn, 0);
                }, 900);
            });
        }
    }

    /** 水平翻转：scaleX 1→0 换面 →0→1。 */
    private void flip(Card card, boolean isChosen) {
        if (card.flipped) return;
        card.flipped = true;
        card.root.animate().scaleX(0f).setDuration(180).withEndAction(() -> {
            card.back.setVisibility(GONE);
            card.front.setVisibility(VISIBLE);
            card.root.animate().scaleX(1f).setDuration(180).start();
            if (isChosen) {
                if (callback != null) { /* 奖励在确认时统一应用，避免提前进下一层 */ }
                Ui.vibrate(getContext(), new long[]{0, 30, 40, 50});
            }
        }).start();
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
}

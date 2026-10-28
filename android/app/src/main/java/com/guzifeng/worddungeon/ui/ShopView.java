package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/**
 * 词境商店（V12.8.3 动态刷新版）：
 * 商品列表由 BattleView 按难度/层数生成（血药/祝福固定，三卡槽按稀有度权重随机），
 * 支持「刷新商品 · 25 词魂」重抽；词魂不足置灰不可购买。纯前端展示层，钱包与效果由 BattleView 落地。
 */
public class ShopView extends FrameLayout {

    /** 单个商品。card 为卡数据（含 id/name/rarity/effect），仅卡槽非空。 */
    public static class Item {
        public final String kind, name, desc;
        public final int price;
        public final JSONObject card;
        public Item(String kind, String name, String desc, int price, JSONObject card) {
            this.kind = kind; this.name = name; this.desc = desc;
            this.price = price; this.card = card;
        }
    }

    public interface OnShop {
        void onBuy(String kind, int price, JSONObject card);
        void onReroll();
        void onLeave();
    }

    private final OnShop cb;
    private final List<Item> items;
    private int souls;
    private TextView soulLab;

    public ShopView(Context c, int startSouls, List<Item> shopItems, OnShop callback) {
        super(c);
        this.cb = callback;
        this.items = shopItems;
        this.souls = startSouls;
        setClickable(true);
        setBackgroundColor(0xDD08111F);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);   // V12.7.11 商店整体上下居中（内容不满屏时填满视口，配合 col 垂直居中）
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);   // V12.7.11 整列垂直居中；内容超出时正常滚动
        col.setPadding(dp(16), dp(20), dp(16), dp(20));
        sv.addView(col);
        addView(sv, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // 头部
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER);
        head.setPadding(dp(16), dp(16), dp(16), dp(16));
        head.setBackground(Ui.card(16, Ui.CARD, 1, Ui.LINE));
        TextView t = Ui.text(c, "词境商店", 19, Ui.TXT, true);
        t.setGravity(Gravity.CENTER);
        head.addView(t);
        soulLab = Ui.text(c, "用词魂换取补给与卡牌", 12, Ui.MUT, false);
        soulLab.setGravity(Gravity.CENTER);
        soulLab.setPadding(0, dp(5), 0, 0);
        head.addView(soulLab);
        col.addView(head);
        col.addView(spacer(12));

        // 商品行
        if (items != null) {
            for (Item it : items) col.addView(item(it));
        }
        col.addView(spacer(10));

        // 刷新商品（25 词魂，重抽卡槽与价格）
        final boolean canReroll = souls >= 25;
        final TextView reroll = Ui.text(c, "刷新商品 · 25 词魂", 13, canReroll ? 0xFF081326 : 0xFF7A8494, true);
        reroll.setGravity(Gravity.CENTER);
        reroll.setPadding(dp(14), dp(12), dp(14), dp(12));
        reroll.setBackground(canReroll ? Ui.gradient(999, 0xFF4DD0E1, 0xFF2B9BC0, 0)
                : Ui.card(999, 0xFF2A3442, 0, 0));
        reroll.setClickable(canReroll);
        reroll.setOnClickListener(v -> {
            if (souls < 25) return;
            souls -= 25;
            cb.onReroll();
        });
        col.addView(reroll);
        col.addView(spacer(14));

        TextView leave = Ui.text(c, "离开商店", 14.5f, Ui.TXT, true);
        leave.setGravity(Gravity.CENTER);
        leave.setPadding(dp(14), dp(13), dp(14), dp(13));
        leave.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        Ui.pressable(leave);
        leave.setOnClickListener(v -> cb.onLeave());
        col.addView(leave);

        refreshSouls();
    }

    private LinearLayout item(Item it) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        int nameColor = Ui.TXT;
        if (it.card != null) {
            int rare = it.card.optInt("rarity", 0);
            nameColor = rare >= 2 ? Ui.PURPLE : rare == 1 ? Ui.GOLD : Ui.CYAN;
        }
        TextView n = Ui.text(getContext(), it.name, 13.5f, nameColor, true);
        TextView d = Ui.text(getContext(), it.desc, 10.5f, Ui.MUT, false);
        tx.addView(n);
        tx.addView(d);
        tx.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(tx);
        final boolean afford = souls >= it.price;   // V12.7.11 词魂不足置灰：灰底灰字、不可购买
        final TextView buy = Ui.text(getContext(), it.price + " 词魂", 12.5f,
                afford ? 0xFF081326 : 0xFF7A8494, true);
        buy.setGravity(Gravity.CENTER);
        buy.setPadding(dp(12), dp(9), dp(12), dp(9));
        buy.setBackground(afford ? Ui.gradient(999, 0xFFFCD34D, 0xFFF59E0B, 0)
                : Ui.card(999, 0xFF2A3442, 0, 0));
        buy.setClickable(afford);
        buy.setOnClickListener(v -> {
            if (souls < it.price) return;
            souls -= it.price;
            cb.onBuy(it.kind, it.price, it.card);
            refreshSouls();
            buy.setEnabled(false);
            buy.setAlpha(0.5f);
        });
        row.addView(buy);
        row.setTag(buy);
        return row;
    }

    private void refreshSouls() {
        if (soulLab != null) soulLab.setText("用词魂换取补给与卡牌 · 持有 " + souls);
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

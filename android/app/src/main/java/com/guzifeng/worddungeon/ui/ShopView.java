package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 词境商店（V7.0 startShop 行 2467；截图 11-shop）：
 * 每层固定节点 —— 血药 30 / 临时祝福 40（下一层伤害+25%）/ 普通卡 50 / 稀有卡 85 / 史诗卡 150，
 * 用「词魂」购买。纯前端展示层；词魂钱包与效果由 BattleView 落地。
 */
public class ShopView extends FrameLayout {

    public interface OnShop {
        void onBuy(String kind, int price);
        void onLeave();
    }

    private final OnShop cb;
    private int souls;
    private TextView soulLab;

    public ShopView(Context c, int startSouls, OnShop callback) {
        super(c);
        this.cb = callback;
        this.souls = startSouls;
        setClickable(true);
        setBackgroundColor(0xDD08111F);

        ScrollView sv = new ScrollView(c);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
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

        // 商品行（kind, 名, 描述, 价格）
        col.addView(item("heal", "血药", "恢复 30 点生命", 30));
        col.addView(item("bless", "临时祝福", "下一层伤害 +25%", 40));
        col.addView(item("card", "普通卡 · 连击大师", "连击 ≥8 时，伤害 +50%（展示侧）", 50));
        col.addView(item("card", "稀有卡 · 灵光乍现", "每题 15% 概率暴击，造成双倍伤害（展示侧）", 85));
        col.addView(item("card", "史诗卡 · 致命一击", "暴击伤害 ×3，获得 50 XP（展示侧）", 150));
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

    private LinearLayout item(String kind, String name, String desc, int price) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(Ui.card(12, Ui.CARD, 1, Ui.LINE));
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView n = Ui.text(getContext(), name, 13.5f, Ui.TXT, true);
        TextView d = Ui.text(getContext(), desc, 10.5f, Ui.MUT, false);
        tx.addView(n);
        tx.addView(d);
        tx.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(tx);
        final TextView buy = Ui.text(getContext(), price + " 词魂", 12.5f, 0xFF081326, true);
        buy.setGravity(Gravity.CENTER);
        buy.setPadding(dp(12), dp(9), dp(12), dp(9));
        buy.setBackground(Ui.gradient(999, 0xFFFCD34D, 0xFFF59E0B, 0));
        buy.setClickable(true);
        buy.setOnClickListener(v -> {
            if (souls < price) return;
            souls -= price;
            cb.onBuy(kind, price);
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

package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.core.CardCatalog;

import org.json.JSONObject;

/**
 * V12.9 词魂祭坛（元成长页）：精华余额 + 6 项永久祝福升级。
 * 数据存 meta.essence / meta.altar；升级走 /altar/upgrade，每局自动生效。
 */
public class AltarView extends LinearLayout {

    private final MainActivity act;
    private LinearLayout listHost;
    private TextView essenceNum;

    // 祭坛展示元数据：{id, 图标, 名称, 每级效果单位描述}
    private static final String[][] META = {
            {"edge",   "⚔️", "词锋", "基础伤害 +"},
            {"vigor",  "❤️", "体魄", "最大生命 +"},
            {"magnet", "🧲", "引力", "开局战魂 +"},
            {"seer",   "👁️", "先知", "首节点翻牌 +"},
            {"revive", "♾️", "复苏", "每局免死复活 "},
            {"greed",  "🪙", "贪婪", "战魂产出 +"},
    };

    public AltarView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(22));
        sv.addView(body);
        addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // 顶栏
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(c, "‹", 26, Ui.TXT, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(4), 0, dp(10), 0);
        back.setClickable(true);
        back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        top.addView(back);
        TextView title = Ui.text(c, "词魂祭坛", 19, Ui.TXT, true);
        top.addView(title, lp(0, -2, 1f));
        body.addView(top);
        body.addView(spacer(12));

        // 精华余额巨卡
        LinearLayout essenceCard = new LinearLayout(c);
        essenceCard.setOrientation(VERTICAL);
        essenceCard.setGravity(Gravity.CENTER);
        essenceCard.setPadding(dp(16), dp(18), dp(16), dp(18));
        essenceCard.setBackground(Ui.card(16, Ui.CARD, 1, Ui.GOLD));
        TextView eLab = Ui.text(c, "✦ 精华", 13, Ui.GOLD, true);
        essenceCard.addView(eLab);
        essenceNum = Ui.text(c, "0", 34, Ui.GOLD, true);
        essenceNum.setGravity(Gravity.CENTER);
        essenceCard.addView(essenceNum);
        TextView eHint = Ui.text(c, "局内战魂局末凝为精华，用于兑换永久祝福", 10.5f, Ui.MUT, false);
        eHint.setGravity(Gravity.CENTER);
        eHint.setPadding(0, dp(6), 0, 0);
        essenceCard.addView(eHint);
        body.addView(essenceCard);
        body.addView(spacer(14));

        listHost = new LinearLayout(c);
        listHost.setOrientation(VERTICAL);
        body.addView(listHost);
    }

    /** 读取 meta 并重建 6 张祭坛卡（后台取数，主线程渲染）。 */
    public void refresh() {
        if (act.router() == null) return;
        new Thread(() -> {
            JSONObject meta = null;
            try {
                meta = act.router().dispatch("/bank/meta", "GET", null);
            } catch (Throwable ignore) {}
            final JSONObject m = meta;
            act.main().post(() -> {
                int essence = 0;
                JSONObject altar = null;
                if (m != null) {
                    // /bank/meta 可能不直接含 essence；以 stats/daily 为准更稳
                }
                try {
                    JSONObject stats = act.router().dispatch("/stats/daily", "GET", null);
                    if (stats != null) {
                        essence = stats.optInt("essence", 0);
                        altar = stats.optJSONObject("altar");
                    }
                } catch (Throwable ignore) {}
                if (altar == null) altar = new JSONObject();
                final int fe = essence;
                final JSONObject fa = altar;
                render(fe, fa);
            });
        }).start();
    }

    private void render(int essence, JSONObject altar) {
        essenceNum.setText(String.valueOf(essence));
        listHost.removeAllViews();
        for (int i = 0; i < META.length; i++) {
            String id = META[i][0];
            int level = altar.optInt(id, 0);
            listHost.addView(buildCard(id, META[i][1], META[i][2], META[i][3], level, essence));
            if (i < META.length - 1) listHost.addView(spacer(10));
        }
    }

    private LinearLayout buildCard(String id, String icon, String name, String unit, int level, int essence) {
        int max = CardCatalog.altarMax(id);
        int cost = CardCatalog.altarCost(id, level);
        boolean maxed = cost < 0;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));

        TextView ic = Ui.text(getContext(), icon, 22, Ui.TXT, false);
        card.addView(ic);
        card.addView(Ui.spacer(getContext(), 10));

        LinearLayout mid = new LinearLayout(getContext());
        mid.setOrientation(VERTICAL);
        TextView nm = Ui.text(getContext(), name, 14.5f, Ui.TXT, true);
        mid.addView(nm);
        // 等级圆点
        mid.addView(dots(max, level));
        // 效果：当前 → 下一级
        TextView eff = Ui.text(getContext(), effectText(id, unit, level, maxed), 11.5f, Ui.MUT, false);
        eff.setPadding(0, dp(4), 0, 0);
        mid.addView(eff);
        card.addView(mid, lp(0, -2, 1f));

        // 升级按钮 / 满级态
        if (maxed) {
            TextView done = Ui.text(getContext(), "已满级", 12, Ui.MUT, true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(dp(12), dp(10), dp(12), dp(10));
            done.setBackground(Ui.card(10, Ui.BG_PILL, 1, Ui.LINE));
            card.addView(done);
        } else {
            boolean afford = essence >= cost;
            TextView btn = Ui.text(getContext(), "✦ " + cost, 13, afford ? 0xFF0B1424 : Ui.MUT, true);
            btn.setGravity(Gravity.CENTER);
            btn.setPadding(dp(12), dp(10), dp(12), dp(10));
            btn.setBackground(Ui.card(10, afford ? Ui.GOLD : Ui.BG_PILL, 1, afford ? Ui.GOLD : Ui.LINE));
            btn.setClickable(true);   // V13.24：禁用态仍可点，点击只弹解释
            if (afford) {
                Ui.pressable(btn);
                btn.setOnClickListener(v -> doUpgrade(id));
            } else {
                btn.setOnClickListener(v -> android.widget.Toast.makeText(getContext(),
                        "精华不足：需要 " + cost + "，当前 " + essence + "；局内战魂会在局末凝为精华",
                        android.widget.Toast.LENGTH_SHORT).show());
            }
            card.addView(btn);
        }
        return card;
    }

    /** 等级圆点：当前等级亮金，其余暗。 */
    private LinearLayout dots(int max, int level) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setPadding(0, dp(5), 0, 0);
        for (int i = 0; i < max; i++) {
            View d = new View(getContext());
            int sz = dp(7);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(sz, sz);
            pl.setMargins(0, 0, dp(4), 0);
            d.setLayoutParams(pl);
            d.setBackground(Ui.card(999, i < level ? Ui.GOLD : Ui.LINE, 0, 0));
            row.addView(d);
        }
        return row;
    }

    private String effectText(String id, String unit, int level, boolean maxed) {
        String cur = unit + valueAt(id, level);
        if (maxed) return cur;
        return cur + "  →  " + unit + valueAt(id, level + 1);
    }

    /** 某项在指定等级的效果数值。 */
    private int valueAt(String id, int level) {
        switch (id) {
            case "edge":   return level;
            case "vigor":  return level * 10;
            case "magnet": return level * 15;
            case "seer":   return level;
            case "revive": return level;
            case "greed":  return level * 8;
            default: return level;
        }
    }

    private void doUpgrade(String id) {
        new Thread(() -> {
            JSONObject res = null;
            try {
                JSONObject b = new JSONObject();
                b.put("id", id);
                res = act.router().dispatch("/altar/upgrade", "POST", b);
            } catch (Throwable ignore) {}
            final JSONObject r = res;
            act.main().post(() -> {
                if (r != null && r.optBoolean("ok", false)) {
                    refresh();
                } else {
                    String reason = r == null ? "升级失败" : r.optString("reason", "升级失败");
                    android.widget.Toast.makeText(getContext(),
                            "essence_not_enough".equals(reason) ? "精华不足" : "无法升级",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
    private LinearLayout.LayoutParams lp(int w, int h, float wgt) {
        return new LinearLayout.LayoutParams(
                w == -2 ? LinearLayout.LayoutParams.WRAP_CONTENT : w,
                h == -2 ? LinearLayout.LayoutParams.WRAP_CONTENT : h,
                wgt);
    }
}

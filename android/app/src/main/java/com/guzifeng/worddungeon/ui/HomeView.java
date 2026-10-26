package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.App;
import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/** 主页（V7.0：home-banner 等级横幅 + launch-card 发射卡 + home-dock 三入口） */
public class HomeView extends LinearLayout {

    private final MainActivity act;

    private TextView lvlBadge, streakTxt, xpLab;
    private View fillView;
    private LinearLayout xpBarWrap;
    private TextView modeDescLab;
    private LinearLayout tabCampaign, tabEndless, tabQuick;
    private LinearLayout cfgArea, quickArea;
    private TextView cfgLineTxt, cfgCountTxt;
    private TextView launchBtn;
    private LinearLayout launchWrap;
    private TextView wrongCntTxt, achCntTxt;
    private TextView[] quickBtns = new TextView[3];
    private String[] quickIds = {"normal", "hard", "hell"};
    private String[] quickNames = {"普通", "困难", "地狱"};

    private String pickMode = "campaign";

    public HomeView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setPadding(dp(18), dp(24), dp(18), dp(20));
        setBackground(new AmbientBackground(28));   // V11.3 地下城氛围底

        buildBanner();
        buildLaunchCard();
        // 构造期即按默认 campaign 模式落定可见性，保证后端数据加载前后整体高度一致
        updateLaunchCard();
        buildDock();
        playEntrance();   // V11.3 入场仪式
    }

    // ── ① home-banner：等级圆徽 + 标题 + 连续天数 + XP 进度 ──
    private void buildBanner() {
        LinearLayout banner = new LinearLayout(getContext());
        banner.setOrientation(VERTICAL);
        banner.setPadding(dp(16), dp(16), dp(16), dp(14));
        banner.setBackground(Ui.card(16, Ui.BG1, 1, Ui.LINE));

        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        lvlBadge = Ui.goldBadge(getContext(), "1", 42);
        singleLine(lvlBadge);
        top.addView(lvlBadge);

        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(VERTICAL);
        LinearLayout.LayoutParams tl = lp(0, -2, 1f);
        titles.setLayoutParams(tl);
        TextView h1 = Ui.text(getContext(), "单词地下城", 21, Ui.TXT, true);
        h1.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(h1);
        TextView sub = Ui.text(getContext(), "词灵使 Lv.1 · 应试单词战斗", 11, Ui.MUT, false);
        titles.addView(sub);
        top.addView(titles);

        streakTxt = Ui.text(getContext(), "🔥 0 天", 13, Ui.GOLD, true);
        singleLine(streakTxt);
        top.addView(streakTxt);
        banner.addView(top);

        xpBarWrap = new LinearLayout(getContext());
        xpBarWrap.setOrientation(HORIZONTAL);
        xpBarWrap.setPadding(0, dp(4), 0, 0);
        // 构造期一次性建好固定高度 XP 条容器；刷新只更新内部 fill 的 weight，
        // 禁止 removeAllViews() 重建，从根上消除条容器高度变化
        View[] barInit = Ui.bar(getContext(), 0f, 0xFF22C55E, 0xFF4ADE80, 7);
        xpBarWrap.addView(barInit[0]);
        fillView = barInit[1];
        banner.addView(xpBarWrap);

        xpLab = Ui.text(getContext(), "0 / 120 XP · 目标成功率 85%", 10.5f, Ui.MUT, false);
        xpLab.setPadding(0, dp(5), 0, 0);
        singleLine(xpLab);
        banner.addView(xpLab);
        addView(banner);
        addView(spacer(12));
    }

    // ── ② launch-card：模式 tab + 配置行/快刷档 + 金色开始按钮 ──
    private void buildLaunchCard() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(13), dp(13), dp(13), dp(13));
        card.setBackground(Ui.gradient(16, Ui.CARD, Ui.CARD2, 90));

        // mode-tabs
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setOrientation(HORIZONTAL);
        tabCampaign = modeTab(tabs, "⚔️", "远征", 0xFF1D4ED8, 0xFF38BDF8);
        tabEndless = modeTab(tabs, "🌀", "无尽", 0xFF7C3AED, 0xFFDB2777);
        tabQuick = modeTab(tabs, "⚡", "快刷", 0xFFD97706, 0xFFFBBF24);
        card.addView(tabs);

        // cfg-line / quick-levels 区（动态）
        // 两区固定高度 48dp：模式切换时仅 VISIBLE/GONE 互换，总高度恒定
        cfgArea = new LinearLayout(getContext());
        cfgArea.setOrientation(VERTICAL);
        cfgArea.setGravity(Gravity.CENTER_VERTICAL);
        cfgArea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        cfgLineTxt = Ui.text(getContext(), "词书 高考 · 难度 自适应", 14, Ui.TXT, true);
        singleLine(cfgLineTxt);
        cfgCountTxt = Ui.text(getContext(), "3674 词 ›", 12, Ui.GOLD, false);
        singleLine(cfgCountTxt);
        LinearLayout cfgRow = new LinearLayout(getContext());
        cfgRow.setOrientation(HORIZONTAL);
        cfgRow.setGravity(Gravity.CENTER_VERTICAL);
        cfgRow.setPadding(dp(12), dp(10), dp(12), dp(10));
        cfgRow.setBackground(Ui.card(10, Ui.BG_PILL, 1, Ui.LINE));
        cfgRow.addView(cfgLineTxt, lp(0, -2, 1f));
        cfgRow.addView(cfgCountTxt);
        Ui.pressable(cfgRow);
        cfgRow.setOnClickListener(v -> act.show(MainActivity.Screen.SETTINGS));
        cfgArea.addView(cfgRow);
        cfgArea.setVisibility(GONE);
        card.addView(cfgArea);

        // quick-levels 三档
        quickArea = new LinearLayout(getContext());
        quickArea.setOrientation(HORIZONTAL);
        quickArea.setGravity(Gravity.CENTER_VERTICAL);
        quickArea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        String[] subs = {"高频优先·识别快刷", "错词加权·进阶", "拼写为主·拼写压轴"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(6), dp(10), dp(6), dp(10));
            box.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.LINE));
            TextView b = Ui.text(getContext(), quickNames[i], 14, Ui.TXT, true);
            TextView s = Ui.text(getContext(), subs[i], 9.5f, Ui.MUT, false);
            s.setGravity(Gravity.CENTER);
            box.addView(b);
            box.addView(s);
            Ui.pressable(box);
            box.setOnClickListener(v -> {
                act.quickLevel = quickIds[idx];
                act.savePrefs();   // GAP-08：持久化快刷档
                for (int j = 0; j < quickBtns.length; j++) {
                    LinearLayout target = (LinearLayout) quickBtns[j].getParent();
                    target.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.LINE));
                }
                box.setBackground(Ui.card(12, 0xFF1E2F1E, 2, Ui.GOLD));
            });
            quickBtns[i] = b;
            quickArea.addView(box, lp(0, -2, 1f));
        }
        quickArea.setVisibility(GONE);
        card.addView(quickArea);
        addView(card);
        addView(spacer(12));

        // launch-btn 金色大按钮
        launchWrap = new LinearLayout(getContext());
        launchWrap.setOrientation(VERTICAL);
        launchBtn = Ui.text(getContext(), "开始远征", 16.5f, 0xFF081326, true);
        launchBtn.setGravity(Gravity.CENTER);
        launchBtn.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientBuilder gb = new GradientBuilder(13, new int[]{0xFFFCD34D, 0xFFF59E0B});
        launchBtn.setBackground(gb.build());
        modeDescLab = Ui.text(getContext(), "3 层闯关", 10.5f, 0xFF5B4310, false);
        modeDescLab.setGravity(Gravity.CENTER);
        singleLine(modeDescLab);
        Ui.ctaGlow(launchBtn);   // V11.3 持续发光脉动 + 按压变暗
        launchBtn.setOnClickListener(v -> { act.mode = pickMode; act.show(MainActivity.Screen.BATTLE); });
        launchWrap.addView(launchBtn);
        launchWrap.addView(modeDescLab);
        launchWrap.setPadding(0, dp(13), 0, 0);
        addView(launchWrap);
        addView(spacer(16));
    }

    /** 渐变背景 helper */
    static class GradientBuilder {
        private final android.graphics.drawable.GradientDrawable g;

        GradientBuilder(int radius, int[] colors) {
            g = new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TL_BR, colors);
            g.setCornerRadius(Ui.dp(App.ctx(), radius));
        }

        android.graphics.drawable.GradientDrawable build() {
            return g;
        }
    }

    // ── ③ home-dock：错词本 / 成就 / 设置 ──
    private void buildDock() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.addView(dockItem("📕", "错词本", "0 词待复习", () -> act.show(MainActivity.Screen.WRONGBOOK), 0), lp(0, -2, 1f));
        row.addView(dockItem("🏆", "成就", "0/14", () -> act.show(MainActivity.Screen.ACHIEVEMENTS), 1), lp(0, -2, 1f));
        row.addView(dockItem("⚙️", "设置", "词书·难度·玩法", () -> act.show(MainActivity.Screen.SETTINGS), 2), lp(0, -2, 1f));
        addView(row);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (getBackground() instanceof AmbientBackground) ((AmbientBackground) getBackground()).stop();
        super.onDetachedFromWindow();
    }

    /** V11.3 入场仪式：banner / 发射卡 / 按钮 / dock 依次淡入上浮。 */
    private void playEntrance() {
        if (getChildCount() < 7) return;
        Ui.entrance(getChildAt(0), 60);    // banner
        Ui.entrance(getChildAt(2), 180);   // launch card
        Ui.entrance(getChildAt(4), 300);   // launchWrap（CTA）
        Ui.entrance(getChildAt(6), 430);   // dock
    }

    private LinearLayout dockItem(String icon, String name, String sub, Runnable onTap, int idx) {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6), dp(12), dp(6), dp(12));
        box.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        TextView ic = Ui.text(c, icon, 20, Ui.TXT, false);
        TextView n = Ui.text(c, name, 13, Ui.TXT, true);
        TextView s = Ui.text(c, sub, 10.5f, Ui.MUT, false);
        singleLine(s);
        box.addView(ic);
        box.addView(n);
        box.addView(s);
        if (idx == 0) wrongCntTxt = s;
        if (idx == 1) achCntTxt = s;
        Ui.pressable(box);
        box.setOnClickListener(v -> onTap.run());
        return box;
    }

    /** 模式 tab（选中彩色描边发光） */
    private LinearLayout modeTab(LinearLayout parent, String icon, String name, int c1, int c2) {
        Context c = getContext();
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(10));
        box.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.LINE));
        TextView ic = Ui.text(c, icon, 18, Ui.TXT, false);
        box.addView(ic);
        LinearLayout tt = new LinearLayout(c);
        tt.setOrientation(VERTICAL);
        TextView b = Ui.text(c, name, 14.5f, Ui.TXT, true);
        TextView s = Ui.text(c, subOf(name), 10.5f, Ui.MUT, false);
        tt.addView(b);
        tt.addView(s);
        LinearLayout.LayoutParams tl = lp(0, -2, 1f);
        tt.setLayoutParams(tl);
        box.addView(tt);
        final int sel1 = c1, sel2 = c2;
        Ui.pressable(box);
        box.setOnClickListener(v -> {
            pickMode = modeIdOf(name);
            refreshTabs(sel1, sel2);
            updateLaunchCard();
        });
        parent.addView(box, lp(0, -2, 1f));
        if ("campaign".equals(modeIdOf(name))) {
            tabCampaign = box;
            box.setBackground(selTab(c1, c2));
        } else if ("endless".equals(modeIdOf(name))) tabEndless = box;
        else tabQuick = box;
        return box;
    }

    private String subOf(String name) {
        if ("远征".equals(name)) return "3 层闯关";
        if ("无尽".equals(name)) return "无限深渊";
        return "刷词冲刺";
    }

    private String modeIdOf(String name) {
        if ("远征".equals(name)) return "campaign";
        if ("无尽".equals(name)) return "endless";
        return "quick";
    }

    private android.graphics.drawable.Drawable selTab(int c1, int c2) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{c1, c2});
        g.setStroke(dp(2), c2);
        g.setCornerRadius(dp(12));
        return g;
    }

    private void refreshTabs(int sel1, int sel2) {
        tabCampaign.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.LINE));
        tabEndless.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.LINE));
        tabQuick.setBackground(Ui.card(12, Ui.BG_PILL, 1, Ui.LINE));
        LinearLayout target = "campaign".equals(pickMode) ? tabCampaign : "endless".equals(pickMode) ? tabEndless : tabQuick;
        target.setBackground(selTab(sel1, sel2));
    }

    private void updateLaunchCard() {
        boolean quick = "quick".equals(pickMode);
        cfgArea.setVisibility(quick ? GONE : VISIBLE);
        quickArea.setVisibility(quick ? VISIBLE : GONE);
        if (quick) {
            launchBtn.setText("开始快刷");
            modeDescLab.setText(quickNames[indexOf(act.quickLevel)] + "档 · 高频词快速刷");
        } else {
            launchBtn.setText("开始" + ("campaign".equals(pickMode) ? "远征" : "无尽"));
            String desc = "campaign".equals(pickMode) ? "3 层闯关" : "无上限下潜";
            int best = act.lastBest;
            if (best > 0 && "endless".equals(pickMode)) desc = "最高纪录 第 " + best + " 层";
            modeDescLab.setText(desc);
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < quickIds.length; i++) if (quickIds[i].equals(id)) return i;
        return 0;
    }

    // ── 数据刷新 ──
    public void onCrashInfo(String crash) {
        String shortC = crash.replaceAll("[\\r\\n]+", " → ");
        shortC = shortC.length() > 180 ? shortC.substring(0, 180) + "…" : shortC;
        TextView c = Ui.text(getContext(), "⚠ 上次启动异常：" + shortC, 11, Ui.RED, false);
        c.setPadding(dp(10), dp(8), dp(10), dp(8));
        c.setBackground(Ui.card(10, Ui.CARD, 1, Ui.RED));
        addView(c, 1);
    }

    public void onBackendState(boolean ready, String err) {
        if (!ready) {
            TextView c = Ui.text(getContext(), "● 后端异常：" + (err == null ? "未知" : err), 13, Ui.RED, false);
            c.setPadding(dp(10), dp(8), dp(10), dp(8));
            c.setBackground(Ui.card(10, Ui.CARD, 1, Ui.RED));
            addView(c, 1);
        }
    }

    public void refreshMeta() {
        if (act.router() == null) return;
        try {
            JSONObject stats = act.router().dispatch("/stats/daily", "GET", null);
            JSONObject daily = stats.optJSONObject("daily");
            JSONObject meta = act.router().dispatch("/bank/meta", "GET", null);
            act.bankMeta = meta;
            JSONObject counts = meta.getJSONObject("counts");
            JSONObject wb = act.router().dispatch("/wrongbook", "GET", null);
            int wrongCnt = wb.optJSONObject("summary") == null ? 0 : wb.optJSONObject("summary").optInt("total", 0);
            JSONArray ach = stats.optJSONArray("ach");
            int achCnt = ach == null ? 0 : ach.length();
            int level = stats.optInt("level", 1);
            int xp = stats.optInt("xp", 0);
            int xpNeed = stats.optInt("xp_needed", 120);
            int streak = stats.optInt("streak", 0);
            int best = stats.optInt("endless_best", 0);
            act.lastBest = best;

            lvlBadge.setText(String.valueOf(level));
            streakTxt.setText("🔥 " + streak + " 天");
            xpLab.setText(xp + " / " + xpNeed + " XP · 目标成功率 85%");
            rebuildXpBar((float) xp / Math.max(1, xpNeed));

            // 词书
            JSONArray books = meta.getJSONArray("books");
            String bookName = "全部";
            int bookTotal = meta.getInt("total");
            for (int i = 0; i < books.length(); i++) {
                JSONObject b = books.getJSONObject(i);
                if (act.book.equals(b.optString("id"))) {
                    bookName = b.optString("name");
                    bookTotal = counts.optInt(b.optString("id"), 0);
                }
            }
            String diffName = diffNameOf(act.difficulty);
            cfgLineTxt.setText("词书 " + bookName + " · 难度 " + diffName);
            cfgCountTxt.setText(bookTotal + " 词 ›");

            wrongCntTxt.setText(wrongCnt + " 词待复习");
            achCntTxt.setText(achCnt + "/14");
            updateLaunchCard();
        } catch (Throwable e) {
            View old = findViewWithTag("meta_err");
            if (old != null) removeView(old);
            TextView c = Ui.text(getContext(), "● 数据加载失败：" + e.getMessage(), 12, Ui.RED, false);
            c.setTag("meta_err");
            c.setPadding(dp(10), dp(8), dp(10), dp(8));
            c.setBackground(Ui.card(10, Ui.CARD, 1, Ui.RED));
            addView(c);
        }
    }

    private String diffNameOf(String diff) {
        if ("adaptive".equals(diff)) return "自适应 · θ 配比";
        if ("easy".equals(diff)) return "简单";
        if ("normal".equals(diff)) return "中等";
        if ("hard".equals(diff)) return "困难";
        if ("hell".equals(diff)) return "地狱";
        return "全部";
    }

    private void rebuildXpBar(float pct) {
        // 不再 removeAllViews 重建：只更新 fill 宽度 weight，容器高度恒定
        float p = Math.max(0f, Math.min(1f, pct));
        if (fillView != null) {
            LinearLayout.LayoutParams fp = (LinearLayout.LayoutParams) fillView.getLayoutParams();
            fp.weight = p;
            fillView.setLayoutParams(fp);
        }
    }

    /** 动态文案一律单行 END 截断：文案长短只改宽度、不改行高，防数据刷新前后高度抖动 */
    private static void singleLine(TextView t) {
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
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

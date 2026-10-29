package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.App;
import com.guzifeng.worddungeon.MainActivity;
import com.guzifeng.worddungeon.core.GameConfig;

import org.json.JSONArray;
import org.json.JSONObject;

/** 主页（V7.0：home-banner 等级横幅 + launch-card 发射卡 + home-dock 三入口） */
public class HomeView extends LinearLayout {

    private final MainActivity act;

    private TextView lvlBadge, streakTxt, xpLab;
    private TextView titleSub;   // V13.0：横幅副标题（称号动态名 + 4 阶样式）
    private TextView goalTxt;   // R3：banner 内「今日目标进度」行（无数据时 GONE，只读消费 /stats/daily）
    private android.animation.ObjectAnimator goalPulse;   // V13.19：全完成金色脉冲
    private TextView reviewTxt; // V13.9：banner 内「今日复习建议」行（只读消费 /review/due，无到期时优雅隐藏）
    /** V13.9：到期词超过此阈值时提示「量较大可分批」，不编造精确数字。 */
    private static final int REVIEW_HEAVY_THRESHOLD = 60;
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
    private View examCard;   // V13.3 考试倒计时卡：仅开启时创建，关闭即移除（无残留）
    private String[] quickIds = {"normal", "hard", "hell"};
    private String[] quickNames = {"普通", "困难", "地狱"};

    private String pickMode = "campaign";

    // V13.0：已应用的称号/火花阶段（避免重复启动无限呼吸动画）
    private int appliedTitleStage = -1;
    private int appliedSparkStage = -1;

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
        titleSub = Ui.text(getContext(), "词灵使 · 应试单词战斗", 11, 0xFF94A3B8, false);
        titles.addView(titleSub);
        top.addView(titles);

        streakTxt = Ui.text(getContext(), "🔥 0 天", 13, 0xFF64748B, false);
        singleLine(streakTxt);
        top.addView(streakTxt);
        banner.addView(top);

        xpBarWrap = new LinearLayout(getContext());
        xpBarWrap.setOrientation(HORIZONTAL);
        xpBarWrap.setPadding(0, dp(4), 0, 0);
        // 构造期一次性建好固定高度 XP 条容器；刷新只更新内部 fill 的 weight，
        // 禁止 removeAllViews() 重建，从根上消除条容器高度变化
        View[] barInit = Ui.bar(getContext(), 0f, 0xFF22C55E, 0xFF4ADE80, 7);
        // V13.21：XP 进度条槽可见深蓝底（0xFF1E3A5F），与 banner 背景区分
        barInit[0].setBackground(Ui.card(999, 0xFF1E3A5F, 0, 0));
        xpBarWrap.addView(barInit[0]);
        fillView = barInit[1];
        banner.addView(xpBarWrap);

        xpLab = Ui.text(getContext(), "0 / 120 XP · 目标成功率 85%", 10.5f, Ui.MUT, false);
        xpLab.setPadding(0, dp(5), 0, 0);
        singleLine(xpLab);
        banner.addView(xpLab);

        // R3：今日目标进度行（默认隐藏，refreshMeta 拿到 /stats/daily 后填充并显示）
        goalTxt = Ui.text(getContext(), "", 10.5f, Ui.GOLD, false);
        goalTxt.setPadding(0, dp(4), 0, 0);
        singleLine(goalTxt);
        goalTxt.setVisibility(GONE);
        banner.addView(goalTxt);

        // V13.9：今日复习建议行（只读 /review/due；dueNow=0 时隐藏，不占位）
        reviewTxt = Ui.text(getContext(), "", 10.5f, Ui.CYAN, false);
        // V13.20：浅蓝圆角卡片底（边框 0x223B82F6 + 填充 0x113B82F6）
        reviewTxt.setBackground(Ui.card(10, 0x113B82F6, 1, 0x223B82F6));
        reviewTxt.setPadding(dp(8), dp(4), dp(8), dp(4));
        reviewTxt.setVisibility(GONE);
        banner.addView(reviewTxt);
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
        cfgArea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
        cfgLineTxt = Ui.text(getContext(), "词书 高考 · 难度 自适应", 13, Ui.TXT, true);
        singleLine(cfgLineTxt);
        cfgCountTxt = Ui.text(getContext(), "3674 词 ›", 11, Ui.GOLD, false);
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
        quickArea.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)));
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
                applyQuickLevel();
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
        modeDescLab = Ui.text(getContext(), "5 层远征", 10.5f, 0xFF5B4310, false);
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
        // V12.6.1 弹性空隙：把底部功能入口压到屏幕最下方
        View flex = new View(getContext());
        flex.setLayoutParams(new LinearLayout.LayoutParams(1, 1, 1f));
        addView(flex);
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
        if (getChildCount() < 8) return;
        Ui.entrance(getChildAt(0), 60);    // banner
        Ui.entrance(getChildAt(2), 180);   // launch card
        Ui.entrance(getChildAt(4), 300);   // launchWrap（CTA）
        Ui.entrance(getChildAt(7), 430);   // dock
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
        if ("远征".equals(name)) return "5 层远征";
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

    /** V12.6.1 快刷难度档：按持久化档位刷选中高亮（金边+深绿底），未选中灰底。 */
    private void applyQuickLevel() {
        for (int j = 0; j < quickBtns.length; j++) {
            LinearLayout target = (LinearLayout) quickBtns[j].getParent();
            boolean sel = quickIds[j].equals(act.quickLevel);
            target.setBackground(Ui.card(12, sel ? 0xFF1E2F1E : Ui.BG_PILL, sel ? 2 : 1, sel ? Ui.GOLD : Ui.LINE));
        }
    }

    private void updateLaunchCard() {
        boolean quick = "quick".equals(pickMode);
        cfgArea.setVisibility(quick ? GONE : VISIBLE);
        quickArea.setVisibility(quick ? VISIBLE : GONE);
        if (quick) {
            launchBtn.setText("开始快刷");
            modeDescLab.setText(quickNames[indexOf(act.quickLevel)] + "档 · 高频词快速刷");
            applyQuickLevel();
        } else {
            launchBtn.setText("开始" + ("campaign".equals(pickMode) ? "远征" : "无尽"));
            String desc = "campaign".equals(pickMode) ? "5 层远征" : "无上限下潜";
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
            // R2-B：冷启以权威 souls 余额读回（后端 /stats/daily 携带 souls）
            if (stats.has("souls")) act.souls = stats.optInt("souls", act.souls);

            // R3：今日目标进度行——只读消费 /stats/daily 的 daily 对象，无数据优雅隐藏；
            // 异常字段一律 opt + 默认值，不闪屏
            if (daily != null) {
                int q = daily.optInt("questions", 0);
                int nw = daily.optInt("new_words", 0);
                int run = daily.optInt("run", 0);
                int qg = Math.max(1, daily.optInt("questions_goal", 40));
                int nwg = Math.max(1, daily.optInt("new_words_goal", 15));
                int rg = Math.max(1, daily.optInt("run_goal", 1));
                float pct = (Math.min(q, qg) / (float) qg
                        + Math.min(nw, nwg) / (float) nwg
                        + Math.min(run, rg) / (float) rg) / 3f * 100f;
                // V13.19：完成度视觉强化——单项 ✓ 绿、超额数字金粗、全完成整行金+脉冲
                boolean qDone = q >= qg, nwDone = nw >= nwg, runDone = run >= rg;
                boolean allDone = qDone && nwDone && runDone;
                android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
                int baseColor = allDone ? 0xFFFCD34D : 0xFF94A3B8;
                sb.append("🎯 今日目标 ");
                appendGoalItem(sb, "答对", q, qg, qDone, allDone);
                sb.append(" · ");
                appendGoalItem(sb, "新学", nw, nwg, nwDone, allDone);
                sb.append(" · ");
                appendGoalItem(sb, "远征", run, rg, runDone, allDone);
                sb.append(" · " + Math.round(pct) + "%");
                if (allDone) {
                    // V13.19：全完成时整行替换为完成文案（避免追加导致 ellipsis 截断）
                    sb = new android.text.SpannableStringBuilder("🎯 ✓ 今日目标已完成");
                }
                goalTxt.setText(sb);
                goalTxt.setVisibility(VISIBLE);
                // 全完成：整行金色脉冲
                if (allDone) {
                    goalTxt.setTextColor(0xFFFCD34D);
                    if (goalPulse == null) {
                        goalPulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(goalTxt,
                                android.animation.PropertyValuesHolder.ofFloat("alpha", 1f, 0.55f, 1f));
                        goalPulse.setDuration(1200);
                        goalPulse.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
                        goalPulse.setRepeatMode(android.animation.ObjectAnimator.REVERSE);
                    }
                    goalPulse.start();
                } else {
                    goalTxt.setTextColor(baseColor);
                    if (goalPulse != null) { goalPulse.cancel(); goalPulse = null; goalTxt.setAlpha(1f); }
                }
            } else {
                goalTxt.setVisibility(GONE);
                if (goalPulse != null) { goalPulse.cancel(); goalPulse = null; }
            }

            // V13.9：今日复习建议——纯只读消费 /review/due。
            // 口径：dueNow = learning_count（到期未毕业）+ review_count（到期已毕业），即此刻真正到期该复习的词。
            // 阈值常量 REVIEW_HEAVY_THRESHOLD=60：超过则提示分批；不编造精确数字，只给量级建议。
            try {
                JSONObject dueBody = new JSONObject();
                dueBody.put("book", act.book);
                JSONObject due = act.router().dispatch("/review/due", "POST", dueBody);
                int learning = due.optInt("learning_count", 0);
                int graduated = due.optInt("review_count", 0);
                int dueNow = learning + graduated;
                if (dueNow > 0) {
                    StringBuilder sb = new StringBuilder("📖 今日建议先复习 ")
                            .append(dueNow).append(" 词（到期未学 ").append(learning)
                            .append(" · 已毕业复习 ").append(graduated).append("）");
                    if (dueNow > REVIEW_HEAVY_THRESHOLD) sb.append("，量较大，可分 2–3 批完成");
                    reviewTxt.setText(sb.toString());
                    reviewTxt.setVisibility(VISIBLE);
                } else {
                    reviewTxt.setText("✨ 暂无到期复习，去学新词吧");
                    reviewTxt.setVisibility(VISIBLE);
                }
            } catch (Throwable t) {
                reviewTxt.setVisibility(GONE);
            }

            lvlBadge.setText(String.valueOf(level));
            int tStage = stats.optInt("title_stage", GameConfig.titleStage(level));
            int sStage = stats.optInt("spark_stage", GameConfig.sparkStage(streak));
            applyTitleStyle(tStage);
            applySparkStyle(sStage, streak);
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
            achCntTxt.setText(achCnt + "/" + AchievementsView.ACH_DEFS.length);
            refreshExamCard();   // V13.3：开启才插入倒计时卡，关闭/非法日期则彻底移除
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
        if ("adaptive".equals(diff)) return "自适应";
        if ("easy".equals(diff)) return "简单";
        if ("normal".equals(diff)) return "中等";
        if ("hard".equals(diff)) return "困难";
        if ("hell".equals(diff)) return "地狱";
        return "全部";
    }

    // ── V13.0 称号 4 阶样式：灰蓝素净 → 青蓝 → 金橙加粗+暗金底条 → 紫金渐变+呼吸 ──
    private void applyTitleStyle(int stage) {
        titleSub.setText(GameConfig.TITLE_NAMES[stage] + " · 应试单词战斗");
        titleSub.getPaint().setShader(null);
        titleSub.setBackground(null);
        titleSub.setPadding(0, 0, 0, 0);
        switch (stage) {
            case 0:
                titleSub.setTextColor(0xFF94A3B8);
                titleSub.setTypeface(Typeface.DEFAULT);
                break;
            case 1:
                titleSub.setTextColor(0xFF38BDF8);
                titleSub.setTypeface(Typeface.DEFAULT);
                break;
            case 2:
                titleSub.setTextColor(0xFFFBBF24);
                titleSub.setTypeface(Typeface.DEFAULT_BOLD);
                titleSub.setBackground(Ui.card(6, 0x33FBBF24, 0, 0));
                titleSub.setPadding(dp(6), dp(1), dp(6), dp(2));
                break;
            case 3:
                titleSub.setTypeface(Typeface.DEFAULT_BOLD);
                float tw = titleSub.getPaint().measureText(titleSub.getText().toString());
                titleSub.getPaint().setShader(new LinearGradient(
                        0, 0, tw, 0, 0xFFA78BFA, 0xFFFBBF24, Shader.TileMode.CLAMP));
                if (appliedTitleStage != 3) Ui.pulseLoop(titleSub, 1.04f, 1400);
                break;
        }
        appliedTitleStage = stage;
    }

    // ── V13.0 火花 4 阶样式：暗灰 → 橙黄 → 金红渐变加粗 → 紫金渐变+呼吸 ──
    private void applySparkStyle(int stage, int streak) {
        streakTxt.setText("🔥 " + streak + " 天");
        streakTxt.getPaint().setShader(null);
        switch (stage) {
            case 0:
                streakTxt.setTextColor(0xFF64748B);
                streakTxt.setTypeface(Typeface.DEFAULT);
                break;
            case 1:
                streakTxt.setTextColor(0xFFF59E0B);
                streakTxt.setTypeface(Typeface.DEFAULT);
                break;
            case 2:
                streakTxt.setTypeface(Typeface.DEFAULT_BOLD);
                float sw = streakTxt.getPaint().measureText(streakTxt.getText().toString());
                streakTxt.getPaint().setShader(new LinearGradient(
                        0, 0, sw, 0, 0xFFF97316, 0xFFEF4444, Shader.TileMode.CLAMP));
                break;
            case 3:
                streakTxt.setTypeface(Typeface.DEFAULT_BOLD);
                float sw3 = streakTxt.getPaint().measureText(streakTxt.getText().toString());
                streakTxt.getPaint().setShader(new LinearGradient(
                        0, 0, sw3, 0, 0xFFA78BFA, 0xFFFBBF24, Shader.TileMode.CLAMP));
                if (appliedSparkStage != 3) Ui.pulseLoop(streakTxt, 1.06f, 1300);
                break;
        }
        appliedSparkStage = stage;
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

    // ── V13.3 考试倒计时：开关关闭/日期缺失时完全不创建 View，先移除旧卡保证无残留 ──
    private void refreshExamCard() {
        if (examCard != null) { removeView(examCard); examCard = null; }
        try {
            SharedPreferences sp = getContext().getSharedPreferences("wd_ui", Context.MODE_PRIVATE);
            boolean on = sp.getBoolean("exam_on", false);
            String date = sp.getString("exam_date", "");
            if (!on) return;                       // 关闭：主页无任何倒计时痕迹
            java.util.Calendar exam = SettingsView.examCalendar(date);
            if (exam == null) return;              // 空/非法日期：不渲染（防重装/重置崩溃）

            long days = daysToExam(exam);
            String head; int color;
            if (days > 0) { head = "📅 距考试还有 " + days + " 天"; color = Ui.GOLD; }
            else if (days == 0) { head = "🔥 今天考试，加油！"; color = 0xFF34D399; }
            else { head = "🗓 考试已结束（已过期 " + (-days) + " 天）"; color = Ui.MUT; }

            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(VERTICAL);
            card.setPadding(dp(14), dp(11), dp(14), dp(11));
            card.setBackground(Ui.card(14, Ui.BG1, 1, Ui.GOLD));
            TextView h = Ui.text(getContext(), head, 14, color, true);
            card.addView(h);
            String hint = roundHint(exam, days);
            if (!hint.isEmpty()) {
                TextView t = Ui.text(getContext(), hint, 10.5f, Ui.MUT, false);
                t.setPadding(0, dp(4), 0, 0);
                card.addView(t);
            }
            examCard = card;
            addView(card, 2);   // 插在 banner 之后、发射卡之前
        } catch (Throwable ignore) {}
    }

    /** 本地自然日差：两个日期的时分秒毫秒清零后按天求差（minSdk22 用 Calendar，不用 LocalDate）。 */
    private static long daysToExam(java.util.Calendar exam) {
        java.util.Calendar today = java.util.Calendar.getInstance();
        today.set(java.util.Calendar.HOUR_OF_DAY, 0);
        today.set(java.util.Calendar.MINUTE, 0);
        today.set(java.util.Calendar.SECOND, 0);
        today.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar e = (java.util.Calendar) exam.clone();
        e.set(java.util.Calendar.HOUR_OF_DAY, 0);
        e.set(java.util.Calendar.MINUTE, 0);
        e.set(java.util.Calendar.SECOND, 0);
        e.set(java.util.Calendar.MILLISECOND, 0);
        return Math.round((e.getTimeInMillis() - today.getTimeInMillis()) / 86400000.0);
    }

    /** 轻量提示：按当前词书已学 seen / 总词数 total / 每日新学目标 15（=DAILY_NEW_WORDS）/ 剩余天数，推算能否考前过完一轮。 */
    private String roundHint(java.util.Calendar exam, long days) {
        try {
            if (days <= 0) return "";
            JSONObject bs = act.router().dispatch("/stats/book", "POST", new JSONObject().put("book", act.book));
            int total = bs.optInt("total", 0);
            int seen = bs.optInt("seen", 0);
            int remain = Math.max(0, total - seen);
            int dailyNew = 15;   // = BackendRouter.DAILY_NEW_WORDS / daily.new_words_goal
            if (remain <= 0) return "当前词书已学完一轮，保持间隔复习即可。";
            long canCover = days * (long) dailyNew;
            if (canCover >= remain)
                return "还剩 " + remain + " 词 · 按每日新学 " + dailyNew + " 词，考前可过完一轮。";
            long need = (long) Math.ceil(remain / (double) days);
            return "还剩 " + remain + " 词 · 每天需约 " + need + " 词（目标 " + dailyNew + "），时间偏紧。";
        } catch (Throwable t) {
            return "";
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

    /** V13.19：追加单个目标项——完成项前缀 ✓ 绿，超额数字金粗。 */
    private void appendGoalItem(android.text.SpannableStringBuilder sb, String label, int cur, int goal, boolean done, boolean allDone) {
        int start = sb.length();
        String mark = done ? "✓ " : "";
        sb.append(mark).append(label).append(" ").append(String.valueOf(cur)).append("/").append(String.valueOf(goal));
        if (done) {
            sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF34D399),
                    start, start + mark.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (cur > goal) {
            // 超额数字金色加粗
            int numStart = start + mark.length() + label.length() + 1;
            int numEnd = numStart + String.valueOf(cur).length();
            sb.setSpan(new android.text.style.ForegroundColorSpan(0xFFFCD34D),
                    numStart, numEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    numStart, numEnd, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}

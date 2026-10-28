package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

/** 设置页（V7.0：词书网格 + 难度网格 + 声音开关 + 数据 + 错词本 + 成就墙 + 玩法/关于 + 清空） */
public class SettingsView extends LinearLayout {

    private final MainActivity act;
    private TextView[] bookLines = new TextView[4];
    private LinearLayout[] diffBoxes;   // V12.7 难度按钮引用（refresh 刷新选中高亮）
    private TextView rangeTxt, masteryTxt;
    private LinearLayout barHost;
    private TextView dataRows;
    private TextView wrongCntTxt;
    private LinearLayout achHost;
    private TextView achTitle;

    private String[] bookIds = {"gaokao", "cet4", "cet6", "kaoyan"};
    private String[] bookNames = {"高考", "四级", "六级", "考研"};
    private String[] diffIds = {"adaptive", "all", "easy", "normal", "hard"};
    private String[] diffNames = {"自适应", "全部", "简单", "中等", "困难"};

    public SettingsView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);

        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        LinearLayout body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        body.setPadding(dp(16), dp(14), dp(16), dp(20));
        sv.addView(body);
        addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        buildSubhead(body);
        buildBookCard(body);
        buildSoundCard(body);
        buildDataCard(body);
        buildWrongCard(body);
        buildAchCard(body);
        buildHowtoCard(body);
        buildAboutCard(body);

        TextView reset = Ui.text(c, "清空所有学习进度", 13, Ui.RED, true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(12), dp(12), dp(12), dp(12));
        reset.setBackground(Ui.card(12, Ui.CARD, 1, Ui.RED));
        reset.setClickable(true);
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(c)
                .setTitle("清空所有学习进度？")
                .setMessage("等级 / XP / 词书进度 / 错词本 / 成就将全部重置，此操作不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空", (d, w) -> {
                    try {
                        act.router().dispatch("/progress/reset", "POST", new JSONObject());
                        refresh();
                    } catch (Throwable e) {
                        android.widget.Toast.makeText(c, "清空失败：" + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .show());
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(12);
        body.addView(reset, rl);
    }

    private void buildSubhead(LinearLayout body) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = Ui.text(getContext(), "‹", 26, Ui.TXT, true);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(4), 0, dp(10), 0);
        back.setClickable(true);
        back.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        row.addView(back);
        TextView st = Ui.text(getContext(), "设置", 19, Ui.TXT, true);
        LinearLayout.LayoutParams sl = lp(0, -2, 1f);
        row.addView(st, sl);
        TextView smut = Ui.text(getContext(), "V12.7.11", 11, Ui.MUT, false);
        row.addView(smut);
        body.addView(row);
        body.addView(spacer(10));
    }

    private void buildBookCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        TextView secTitle = tipTitle("词书选择 ⓘ", "切换后即时过滤出题范围；一词可属多本词书。");
        card.addView(secTitle);

        LinearLayout grid = new LinearLayout(getContext());
        grid.setOrientation(HORIZONTAL);
        for (int i = 0; i < bookIds.length; i++) {
            final int idx = i;
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(VERTICAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(6), dp(9), dp(6), dp(9));
            box.setBackground(Ui.card(10, Ui.BG_PILL, 1, Ui.LINE));
            TextView nm = Ui.text(getContext(), bookNames[i], 12.5f, Ui.TXT, true);
            TextView ct = Ui.text(getContext(), "—", 10, Ui.MUT, false);
            box.addView(nm);
            box.addView(ct);
            bookLines[idx] = ct;
            box.setOnClickListener(v -> {
                act.book = bookIds[idx];
                act.savePrefs();   // GAP-08：持久化词书选择
                refresh();
            });
            grid.addView(box, lp(0, -2, 1f));
        }
        card.addView(grid);
        card.addView(spacer(8));

        TextView dh = tipTitle("难度选择 ⓘ", "自适应按能力 θ 抽词；选具体档则锁定 tier。");
        card.addView(dh);
        LinearLayout dgrid = new LinearLayout(getContext());
        dgrid.setOrientation(HORIZONTAL);
        diffBoxes = new LinearLayout[diffIds.length];
        for (int i = 0; i < diffIds.length; i++) {
            final int idx = i;
            LinearLayout box = new LinearLayout(getContext());
            box.setOrientation(HORIZONTAL);
            box.setGravity(Gravity.CENTER);
            box.setPadding(dp(2), dp(9), dp(2), dp(9));
            // V12.6.1 难度选中高亮：青边+青字，未选中保持灰底
            boolean sel = act.difficulty.equals(diffIds[idx]);
            box.setBackground(Ui.card(9, sel ? Ui.BG1 : Ui.BG_PILL, sel ? 2 : 1, sel ? Ui.CYAN : Ui.LINE));
            TextView nm = Ui.text(getContext(), diffNames[i], 12, sel ? Ui.CYAN : Ui.TXT, true);
            box.addView(nm);
            diffBoxes[idx] = box;
            box.setOnClickListener(v -> {
                act.difficulty = diffIds[idx];
                act.savePrefs();   // GAP-08：持久化难度选择
                refresh();
            });
            dgrid.addView(box, lp(0, -2, 1f));
        }
        applyDiffHighlight();   // V12.7 首次构建即按当前档位高亮
        card.addView(dgrid);
        card.addView(spacer(8));

        LinearLayout range = new LinearLayout(getContext());
        range.setOrientation(HORIZONTAL);
        range.setGravity(Gravity.CENTER_VERTICAL);
        range.setPadding(dp(12), dp(9), dp(12), dp(9));
        range.setBackground(Ui.card(10, Ui.BG_PILL, 1, Ui.LINE));
        rangeTxt = Ui.text(getContext(), "当前范围：高考 · 自适应", 12, Ui.TXT, true);
        range.addView(rangeTxt, lp(0, -2, 1f));
        card.addView(range);

        card.addView(spacer(6));
        LinearLayout master = new LinearLayout(getContext());
        master.setOrientation(HORIZONTAL);
        master.setGravity(Gravity.CENTER_VERTICAL);
        masteryTxt = Ui.text(getContext(), "掌握进度（《高考》）", 12.5f, Ui.MUT, false);
        master.addView(masteryTxt, lp(0, -2, 1f));
        card.addView(master);
        barHost = new LinearLayout(getContext());
        barHost.setOrientation(HORIZONTAL);
        card.addView(barHost);
        body.addView(card);
        body.addView(spacer(10));
    }

    private void buildSoundCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        card.addView(tipTitle("声音与触感 ⓘ", "全部本地合成，无外部音频文件；发音为离线 TTS 美式。"));

        SharedPreferences sp = getContext().getSharedPreferences("wd_ui", Context.MODE_PRIVATE);
        card.addView(audioRow("打击音效", "命中 / 暴击 / 复仇 / 受击", "sfx", sp));
        // R3：诚实化——「背景音乐 BGM」开关与「BGM 音量」滑条无任何读取方（前端纯合成音效、无 BGM 引擎），
        // 已移除；保留音效开关 / 音效音量滑条 / 震动 / 发音
        card.addView(audioRow("震动触感", "命中、暴击、受击时震动", "vibrate", sp));
        card.addView(audioRow("单词发音", "进入新卡片自动读美式发音（离线 TTS）", "speak", sp));
        // V7.0 音量滑条（音效；纯展示侧持久化，音频引擎在前端合成层）
        card.addView(volumeRow("音效音量", "vol_sfx", sp, 85));
        body.addView(card);
        body.addView(spacer(10));
    }

    private LinearLayout volumeRow(String name, String key, SharedPreferences sp, int def) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        TextView n = Ui.text(getContext(), name, 13.5f, Ui.TXT, false);
        row.addView(n, lp(0, -2, 1f));
        final android.widget.SeekBar bar = new android.widget.SeekBar(getContext());
        bar.setMax(100);
        int v = sp.getInt(key, def);
        bar.setProgress(v);
        TextView num = Ui.text(getContext(), String.valueOf(v), 12, Ui.GOLD, true);
        bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                sp.edit().putInt(key, progress).apply();
                num.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, -2, 1f);
        bl.leftMargin = dp(10);
        bl.rightMargin = dp(8);
        row.addView(bar, bl);
        row.addView(num);
        return row;
    }

    private LinearLayout audioRow(String name, String sub, String key, SharedPreferences sp) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        LinearLayout lab = new LinearLayout(getContext());
        lab.setOrientation(VERTICAL);
        TextView n = Ui.text(getContext(), name, 14, Ui.TXT, false);
        TextView s = Ui.text(getContext(), sub, 10.5f, Ui.MUT, false);
        lab.addView(n);
        lab.addView(s);
        lab.setLayoutParams(lp(0, -2, 1f));
        row.addView(lab);
        boolean def = "speak".equals(key) && act.speak;
        boolean on = sp.getBoolean(key, def);
        View sw = Ui.switchBtn(getContext(), on);
        row.addView(sw);
        final String k = key;
        sw.setOnClickListener(v -> {
            boolean next = !sp.getBoolean(k, def);
            sp.edit().putBoolean(k, next).apply();
            Ui.applySwitch((LinearLayout) sw, next);
            if ("speak".equals(k)) act.speak = next;
        });
        return row;
    }

    private void buildDataCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        card.addView(Ui.text(getContext(), "数据", 14, Ui.TXT, true));
        dataRows = Ui.text(getContext(), "加载中…", 13, Ui.MUT, false);
        dataRows.setLineSpacing(dp(6), 1f);
        card.addView(dataRows);
        body.addView(card);
        body.addView(spacer(10));
    }

    private void buildWrongCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        card.addView(tipTitle("错词本 ⓘ", "按错误次数分顽固/易错/生疏三组，开始复习顽固优先。"));
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView lab = Ui.text(getContext(), "待复习错词", 13, Ui.MUT, false);
        row.addView(lab, lp(0, -2, 1f));
        wrongCntTxt = Ui.text(getContext(), "0 词", 14, Ui.TXT, true);
        row.addView(wrongCntTxt);
        card.addView(row);
        TextView go = Ui.text(getContext(), "进入错词本复习", 14, Ui.TXT, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(12), dp(11), dp(12), dp(11));
        go.setBackground(Ui.card(12, Ui.BG1, 1, Ui.CYAN));
        go.setClickable(true);
        go.setOnClickListener(v -> act.show(MainActivity.Screen.WRONGBOOK));
        card.addView(go);
        body.addView(card);
        body.addView(spacer(10));
    }

    private void buildAchCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        achTitle = Ui.text(getContext(), "成就墙", 14, Ui.TXT, true);
        card.addView(achTitle);
        achHost = new LinearLayout(getContext());
        achHost.setOrientation(VERTICAL);
        card.addView(achHost);
        body.addView(card);
        body.addView(spacer(10));
    }

    private void buildHowtoCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        card.addView(Ui.text(getContext(), "怎么玩", 14, Ui.TXT, true));
        TextView how = Ui.text(getContext(),
                "· 答对单词攻击敌人，答得越快伤害越高，连击还会再加成。\n"
                        + "· 连击 ≥5 进入心流，额外获得词魂；连续答错会凝神，下一题限时 +2。\n"
                        + "· 题目标注词性（n./v./adj.…），帮助区分同形与近形词。\n"
                        + "· 答错或超时敌人反击，连击清零，错词进错词本并按遗忘曲线复习。",
                12.5f, Ui.MUT, false);
        how.setLineSpacing(dp(4), 1f);
        card.addView(how);
        body.addView(card);
        body.addView(spacer(10));
    }

    private void buildAboutCard(LinearLayout body) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Ui.card(14, Ui.CARD, 1, Ui.LINE));
        card.addView(Ui.text(getContext(), "关于", 14, Ui.TXT, true));
        TextView about = Ui.text(getContext(),
                "词库：开源 ECDICT 词典（7388 词，高考/四级/六级/考研）\n"
                        + "学习算法：θ 自适应难度 + SM-2 间隔复习 + 题型阶梯（判断→认义→写义→拼写）\n"
                        + "模式：远征 5 层远征 · 无尽下潜挑战 · 错词 2 次封顶\n"
                        + "版本：V12.7.11 · 全原生快速刷题（7388 词已内置）",
                12, Ui.MUT, false);
        about.setLineSpacing(dp(4), 1f);
        card.addView(about);
        body.addView(card);
    }

    public void refresh() {
        if (act.router() == null) return;
        try {
            JSONObject meta = act.router().dispatch("/bank/meta", "GET", null);
            JSONObject counts = meta.getJSONObject("counts");
            JSONObject stats = act.router().dispatch("/stats/daily", "GET", null);
            JSONObject daily = stats.optJSONObject("daily");
            JSONObject wb = act.router().dispatch("/wrongbook", "GET", null);
            int wrongCnt = wb.optJSONObject("summary") == null ? 0 : wb.optJSONObject("summary").optInt("total", 0);
            wrongCntTxt.setText(wrongCnt + " 词");

            // 词书选中态与词数
            String bookName = "";
            for (int i = 0; i < bookIds.length; i++) {
                bookLines[i].setText(bookNames[i] + "\n" + counts.optInt(bookIds[i], 0) + " 词");
                View parent = (View) bookLines[i].getParent();
                if (act.book.equals(bookIds[i])) {
                    bookName = bookNames[i];
                    parent.setBackground(Ui.card(10, Ui.BG1, 2, Ui.CYAN));
                } else {
                    parent.setBackground(Ui.card(10, Ui.BG_PILL, 1, Ui.LINE));
                }
            }

            // V12.7 难度选中高亮随点击刷新（修复"高亮锁死"）
            applyDiffHighlight();

            // 范围
            String diffName = diffNameOf(act.difficulty);
            String diffShow = act.difficulty.equals("adaptive") ? "自适应"
                    : act.difficulty.equals("all") ? "全部" : diffName;
            rangeTxt.setText("词书 " + bookName + " · 难度 " + diffShow);

            // 掌握进度
            JSONObject bs = act.router().dispatch("/stats/book", "POST", new JSONObject().put("book", act.book));
            int mastered = bs.optInt("mastered", 0);
            int total = bs.optInt("total", 0);
            masteryTxt.setText("掌握进度（《" + bookName + "》）  " + mastered + " / " + total);
            if (barHost.getChildCount() > 0) barHost.removeAllViews();
            View[] bar = Ui.bar(getContext(), total > 0 ? (float) mastered / total : 0, 0xFF22C55E, 0xFF4ADE80, 6);
            barHost.addView(bar[0]);

            // 数据
            int level = stats.optInt("level", 1);
            int xp = stats.optInt("xp", 0);
            int xpNeed = stats.optInt("xp_needed", 120);
            int streak = stats.optInt("streak", 0);
            double theta = stats.optDouble("theta", 1.5);
            int q = daily == null ? 0 : daily.optInt("questions", 0);
            int nw = daily == null ? 0 : daily.optInt("new_words", 0);
            int run = daily == null ? 0 : daily.optInt("run", 0);
            int dl = stats.optInt("due_learning", 0);
            int dg = stats.optInt("due_graduated", 0);
            dataRows.setText("词灵使等级      Lv." + level + " · " + xp + " / " + xpNeed + " XP\n"
                    + "连续打卡         🔥 " + streak + " 天\n"
                    + "能力 θ           " + String.format("%.2f", theta) + "\n"
                    + "今日目标         答对 " + q + "/40 · 新学 " + nw + "/15 · 远征 " + run + "/1\n"
                    + "今日到期复习     " + dl + " 学习 + " + dg + " 已毕业");

            // 成就墙
            JSONArray ach = stats.optJSONArray("ach");
            rebuildAch(ach == null ? new JSONArray() : ach);
        } catch (Throwable e) {
            dataRows.setText("数据加载失败：" + e.getMessage());
        }
    }

    /** V12.7 难度选中高亮刷新：当前档青边+青字，未选中灰底。 */
    private void applyDiffHighlight() {
        if (diffBoxes == null) return;
        for (int i = 0; i < diffBoxes.length && i < diffIds.length; i++) {
            LinearLayout box = diffBoxes[i];
            boolean sel = act.difficulty.equals(diffIds[i]);
            box.setBackground(Ui.card(9, sel ? Ui.BG1 : Ui.BG_PILL, sel ? 2 : 1, sel ? Ui.CYAN : Ui.LINE));
            TextView nm = (TextView) box.getChildAt(0);
            if (nm != null) nm.setTextColor(sel ? Ui.CYAN : Ui.TXT);
        }
    }

    private void rebuildAch(JSONArray got) {
        if (achHost.getChildCount() > 0) achHost.removeAllViews();
        String[][] defs = {
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
        boolean[] has = new boolean[defs.length];
        for (int i = 0; i < defs.length; i++) {
            for (int j = 0; j < got.length(); j++) {
                if (defs[i][3].equals(got.optString(j))) has[i] = true;
            }
        }
        for (int r = 0; r < 7; r++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                int i = r * 2 + col;
                if (i >= defs.length) break;
                LinearLayout cell = new LinearLayout(getContext());
                cell.setOrientation(HORIZONTAL);
                cell.setGravity(Gravity.CENTER_VERTICAL);
                cell.setPadding(dp(8), dp(8), dp(8), dp(8));
                cell.setBackground(Ui.card(10, has[i] ? Ui.BG1 : Ui.BG_PILL, 1, has[i] ? Ui.LINE : Ui.LINE));
                TextView ic = Ui.text(getContext(), defs[i][0], 17, Ui.TXT, false);
                cell.addView(ic);
                LinearLayout tx = new LinearLayout(getContext());
                tx.setOrientation(VERTICAL);
                TextView nm = Ui.text(getContext(), defs[i][1], 12, has[i] ? Ui.TXT : Ui.MUT, true);
                TextView ds = Ui.text(getContext(), defs[i][2], 10, Ui.MUT, false);
                tx.addView(nm);
                tx.addView(ds);
                tx.setLayoutParams(lp(0, -2, 1f));
                cell.addView(tx);
                cell.setAlpha(has[i] ? 1f : 0.45f);
                row.addView(cell, lp(0, -2, 1f));
            }
            achHost.addView(row);
            if (r < 6) achHost.addView(spacer(7));
        }
        achTitle.setText("成就墙（" + countTrue(has) + "/14）");
    }

    private int countTrue(boolean[] a) {
        int n = 0;
        for (boolean b : a) if (b) n++;
        return n;
    }

    private TextView tipTitle(String s, String tip) {
        TextView t = Ui.text(getContext(), s, 14, Ui.TXT, true);
        t.setClickable(true);
        t.setOnClickListener(v -> android.widget.Toast.makeText(getContext(), tip, android.widget.Toast.LENGTH_LONG).show());
        return t;
    }

    private String diffNameOf(String diff) {
        if ("adaptive".equals(diff)) return "自适应";
        if ("all".equals(diff)) return "全部";
        if ("easy".equals(diff)) return "简单";
        if ("normal".equals(diff)) return "中等";
        if ("hard".equals(diff)) return "困难";
        return diff;
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

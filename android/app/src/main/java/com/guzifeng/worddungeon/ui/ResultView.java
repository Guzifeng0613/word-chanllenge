package com.guzifeng.worddungeon.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.guzifeng.worddungeon.MainActivity;

/** 结算页：胜负 / 层数 / 错词 / 最大连击 */
public class ResultView extends LinearLayout {

    private final MainActivity act;
    private TextView title, info;

    public ResultView(Context c) {
        super(c);
        act = (MainActivity) c;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(24), dp(40), dp(24), dp(24));

        title = Ui.text(c, "", 30, Ui.GOLD, true);
        title.setGravity(Gravity.CENTER);
        addView(title);
        addView(spacer(8));
        info = Ui.text(c, "", 15, Ui.TXT, false);
        info.setGravity(Gravity.CENTER);
        info.setTextIsSelectable(true);
        addView(info);
        addView(spacer(28));

        TextView again = Ui.button(c, "再来一局", Ui.CYAN, Ui.BG0, true);
        again.setOnClickListener(v -> act.show(MainActivity.Screen.BATTLE));
        addView(again, new LinearLayout.LayoutParams(-1, -2));
        addView(spacer(10));
        TextView home = Ui.button(c, "返回主页", Ui.CARD2, Ui.TXT, false);
        home.setOnClickListener(v -> act.show(MainActivity.Screen.HOME));
        addView(home, new LinearLayout.LayoutParams(-1, -2));
    }

    public void onFinish(boolean win, String mode, int layer, int wrong, int combo) {
        title.setText(win ? "🏆 胜利！" : "💀 战败");
        title.setTextColor(win ? Ui.GOLD : Ui.RED);
        String modeName = "campaign".equals(mode) ? "远征" : "endless".equals(mode) ? "无尽" : "快刷";
        info.setText("模式：" + modeName + "\n通关层数：" + layer + "\n单局答错：" + wrong + "\n最高连击：" + combo);
    }

    private int dp(float v) { return Ui.dp(getContext(), v); }
    private View spacer(int h) { return Ui.spacer(getContext(), h); }
}

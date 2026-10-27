package com.guzifeng.worddungeon.core;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 敌人模型（对应前端 ENEMY + buildEnemy）
 * normal/elite 模板 hp/atk 按难度档（数组）；boss 固定 hp/atk 与机制。
 * 无尽以第 1 层数值为基线按深度缩放；冠军额外强化。
 */
public class EnemyModel {

    public static class Tpl {
        String id, name;
        int[] hp, atk;
        String prefer;      // 偏好题型
        boolean confuse;
        int timeMod;        // 限时修正（秒）
        String mech;        // boss 机制 fog/blur/rage
        int every;
        String mechDesc;

        Tpl(String id, String name, int[] hp, int[] atk, String prefer,
            boolean confuse, int timeMod, String mech, int every, String mechDesc) {
            this.id = id; this.name = name; this.hp = hp; this.atk = atk;
            this.prefer = prefer; this.confuse = confuse; this.timeMod = timeMod;
            this.mech = mech; this.every = every; this.mechDesc = mechDesc;
        }
    }

    public final List<Tpl> normal = new ArrayList<>();
    public final List<Tpl> elite = new ArrayList<>();
    public final List<Tpl> boss = new ArrayList<>();
    /** 远征第 5 层最终 Boss（独立于无尽循环的 boss1/2/3）。 */
    public Tpl overlord;
    private final Random rnd;

    public EnemyModel() {
        rnd = WordBank.get().rnd;
        // V12.0：普通/精英 hp/atk 由 3 元素扩为 5 元素（按下标 layer-1，逐层递增）
        normal.add(new Tpl("wordfiend", "词魔", new int[]{32,44,70,76,90}, new int[]{7,9,11,13,15},
                null, false, 0, null, 0, null));
        normal.add(new Tpl("confuser", "易混词妖", new int[]{36,48,74,80,94}, new int[]{8,10,12,14,16},
                "en2cn", true, 0, null, 0, null));
        normal.add(new Tpl("speller", "拼写守卫", new int[]{40,52,78,84,98}, new int[]{9,11,13,15,17},
                "spell", false, 0, null, 0, null));
        elite.add(new Tpl("ghost", "健忘幽灵", new int[]{90,108,130,158,180}, new int[]{11,13,15,17,19},
                null, false, -2, null, 0, null));
        elite.add(new Tpl("polysem", "多义词魔", new int[]{100,118,140,168,190}, new int[]{12,14,16,18,20},
                null, true, 0, null, 0, null));
        // 无尽循环保留原 boss1/2/3（campaign 不再下标取它们）
        boss.add(new Tpl("boss1", "词魇·混沌", new int[]{180}, new int[]{14},
                null, false, 0, "fog", 4, "每答 4 题释放“词雾”，下一题限时减半"));
        boss.add(new Tpl("boss2", "词魇·迷雾", new int[]{240}, new int[]{16},
                null, false, 0, "blur", 3, "每答 3 题，下一题选项高速闪动后定格"));
        boss.add(new Tpl("boss3", "词魇·深渊", new int[]{300}, new int[]{18},
                null, false, 0, "rage", 0, "HP 低于 50% 狂暴：限时 −2 秒、攻击 +4"));
        // 远征第 5 层最终 Boss：高血量 + 拼写为主 + 高攻击（mech 仅描述，前端未接线）
        overlord = new Tpl("overlord", "词魇·深渊领主", new int[]{320}, new int[]{18},
                "spell", false, 2, "rage", 0, "HP 低于 50% 狂暴");
    }

    private static JSONObject tplJson(Tpl t) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("confuse", t.confuse);
            o.put("prefer", t.prefer == null ? JSONObject.NULL : t.prefer);
            o.put("timeMod", t.timeMod);
            o.put("mech", t.mech == null ? JSONObject.NULL : t.mech);
            o.put("every", t.every);
            o.put("mechDesc", t.mechDesc == null ? JSONObject.NULL : t.mechDesc);
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }

    private static JSONObject envelope(Tpl t, String name, int hp, int atk,
                                       boolean isBoss, boolean isElite,
                                       boolean champion, boolean quick) {
        JSONObject o = new JSONObject();
        try {
            o.put("tpl", tplJson(t));
            o.put("name", name);
            o.put("maxHp", hp);
            o.put("hp", hp);
            o.put("atk", atk);
            o.put("boss", isBoss);
            o.put("elite", isElite);
            o.put("champion", champion);
            o.put("quick", quick);
        } catch (Exception e) {
            // 内部结构固定
        }
        return o;
    }

    /** 无尽深度缩放（hp/atk 倍率）。 */
    public static double[] endlessScale(int depth) {
        return new double[]{
                1 + GameConfig.ENDLESS_HP_STEP * (depth - 1),
                1 + GameConfig.ENDLESS_ATK_STEP * (depth - 1)};
    }

    public static boolean isChampionDepth(int depth) {
        return depth % GameConfig.ENDLESS_CHAMPION_EVERY == 0;
    }

    /** 生成敌人。mode: campaign / endless；kind: normal / elite / boss。 */
    public JSONObject spawn(String mode, String kind, int layer) {
        if ("endless".equals(mode)) {
            double[] sc = endlessScale(layer);
            boolean champion = isChampionDepth(layer);
            if ("boss".equals(kind)) {
                Tpl t = boss.get((layer - 1) % boss.size());
                double champMul = champion ? GameConfig.ENDLESS_CHAMPION_HP : 1.0;
                int champAtk = champion ? GameConfig.ENDLESS_CHAMPION_ATK : 0;
                int hp = (int) Math.round(boss.get(0).hp[0] * sc[0] * champMul);
                int atk = (int) Math.round(boss.get(0).atk[0] * sc[1]) + champAtk;
                return envelope(t, champion ? "冠军·" + t.name : t.name, hp, atk,
                        true, false, champion, false);
            }
            List<Tpl> pool = "elite".equals(kind) ? elite : normal;
            Tpl t = pool.get(rnd.nextInt(pool.size()));
            double champMul = champion ? GameConfig.ENDLESS_CHAMPION_HP : 1.0;
            int champAtk = champion ? GameConfig.ENDLESS_CHAMPION_ATK : 0;
            int hp = (int) Math.round(t.hp[0] * sc[0] * champMul);
            int atk = (int) Math.round(t.atk[0] * sc[1]) + champAtk;
            return envelope(t, champion ? "冠军·" + t.name : t.name, hp, atk,
                    false, "elite".equals(kind), champion, false);
        }
        // campaign：层下标 clamp 到合法范围（0..LAYER_COUNT-1）
        int lc = Math.max(1, Math.min(layer, GameConfig.LAYER_COUNT));
        int li = lc - 1;
        if ("boss".equals(kind)) {
            // 第 5 层固定 overlord；理论上更早层不会传 boss，仍 clamp 兜底防越界
            Tpl t = lc >= GameConfig.LAYER_COUNT ? overlord
                    : boss.get(Math.min(li, boss.size() - 1));
            return envelope(t, t.name, t.hp[0], t.atk[0], true, false, false, false);
        }
        List<Tpl> pool = "elite".equals(kind) ? elite : normal;
        Tpl t = pool.get(rnd.nextInt(pool.size()));
        return envelope(t, t.name, t.hp[li], t.atk[li], false, "elite".equals(kind), false, false);
    }

    /** 快刷敌人（固定 hp，atk 8）。 */
    public JSONObject spawnQuick(String level) {
        int hp = GameConfig.QUICK_ENEMY_HP[QuickEngine.levelIndex(level)];
        String name = "normal".equals(level) ? "普通词妖"
                : "hard".equals(level) ? "困难词妖" : "地狱词妖";
        Tpl t = normal.get(0);
        return envelope(t, name, hp, 8, false, false, false, true);
    }
}

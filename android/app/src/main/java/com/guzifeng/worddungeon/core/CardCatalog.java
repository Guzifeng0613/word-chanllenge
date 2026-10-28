package com.guzifeng.worddungeon.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 词典卡目录（V7.0 CARDS，25 张：9 普通 rare0 / 9 稀有 rare1 / 7 史诗 rare2）
 * + 三槽抽取算法（节点类型基础概率 + 层数加成 + pity>=3 保底稀有、epicPity>=6 保底史诗）。
 * 数值抄录自 index.html drawChoice/rollSlotRarity/drawCardOfRarity/rarityTable。
 * 效果的战斗接线由前端按 id 执行；后端只下发目录与抽取结果。
 */
public final class CardCatalog {
    private CardCatalog() {}

    public static class Card {
        public final String id, name, desc;
        public final int rare;
        Card(String id, String name, int rare, String desc) {
            this.id = id; this.name = name; this.rare = rare; this.desc = desc;
        }
        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("rarity", rare);
                o.put("effect", desc);
            } catch (Exception ignore) {}
            return o;
        }
    }

    private static final List<Card> CARDS = new ArrayList<>();
    private static final Map<String, Card> CMAP = new LinkedHashMap<>();

    static {
        // ── 普通 common(0) ──
        add("speednote", "速记", 0, "3 秒内答对的伤害额外 +30%");
        add("combomaster", "连击大师", 0, "连击 ≥ 8 时，伤害 +50%");
        add("steady", "稳扎稳打", 0, "连击 ≥ 5 时，每答对 3 题恢复 2 HP");
        add("shield", "错词护盾", 0, "每场战斗开始时获得 6 点护盾");
        add("buildup", "厚积薄发", 0, "FAMILIAR / MASTERED 的词伤害额外 +20%");
        add("soulmagnet", "词魂引力", 0, "战斗胜利获得的词魂 +40%");
        add("braver", "越战越勇", 0, "每进入新一层，该层伤害 +10%");
        add("firstblood", "先发制人", 0, "每场战斗第一次答对，伤害 +60%");
        add("warmup", "热身", 0, "每场战斗前 3 题，限时 +2 秒");
        // ── 稀有 rare(1) ──
        add("flash", "灵光乍现", 1, "每题 15% 概率暴击，造成双倍伤害");
        add("spellboost", "默写强化", 1, "拼写题伤害 +80%");
        add("spellmaster", "精准默写", 1, "拼写题答对恢复 4 HP，且拼写题再 +20% 伤害");
        add("unstop", "势如破竹", 1, "答错时连击不清零，而是降为一半（向下取整）");
        add("timectrl", "时间掌控", 1, "题目限时 +3 秒；超时受击伤害减半");
        add("retreat", "以退为进", 1, "每次答错后，下一题伤害 +60%（最多叠加 1 层）");
        add("combolightning", "连击闪电", 1, "连击每 +1，伤害额外 +3%（随连击叠加）");
        add("lifesteal", "汲词", 1, "答对时恢复本次伤害 10% 的 HP");
        add("doubleedged", "双刃剑", 1, "造成的伤害 +30%，但受到的伤害 +20%");
        // ── 史诗 epic(2) ──
        add("deadly", "致命一击", 2, "暴击伤害 ×3，并获得 10% 暴击率");
        add("wordfire", "词火", 2, "每连续答对 4 题，造成敌人最大 HP 8% 真实伤害");
        add("critstorm", "暴击风暴", 2, "暴击率 +25%；暴击时连击额外 +1");
        add("spellchain", "拼写连锁", 2, "拼写答对后，下一题强制拼写且伤害 +50%");
        add("glasscannon", "玻璃大炮", 2, "造成的伤害 ×1.8，但最大 HP -30");
        add("timewarp", "时间扭曲", 2, "所有限时 +4 秒；3 秒内答对伤害 +50%");
        add("immortal", "不朽", 2, "每场战斗可免死一次：致命伤害后以 1 HP 存活");
    }

    private static void add(String id, String name, int rare, String desc) {
        Card c = new Card(id, name, rare, desc);
        CARDS.add(c);
        CMAP.put(id, c);
    }

    public static Card byId(String id) { return CMAP.get(id); }

    public static JSONArray catalog() {
        JSONArray arr = new JSONArray();
        for (Card c : CARDS) arr.put(c.toJson());
        return arr;
    }

    /** 商店价格（C.shop）：血药30 / 祝福40 / 卡 普通50/稀有85/史诗150；刷新一组25。 */
    public static final int PRICE_POTION = 30;
    public static final int PRICE_BLESS = 40;
    public static final int PRICE_CARD_COMMON = 50;
    public static final int PRICE_CARD_RARE = 85;
    public static final int PRICE_CARD_EPIC = 150;
    public static final int REROLL_COST = 25;

    /**
     * 商店价格层数系数（V12.8.3）：基础价 ×(1 + 0.04×层数差)，按 5 取整，封顶 2.0 倍。
     * 越到后面技能越难获取：高层同一张卡更贵。
     */
    public static int shopPrice(int base, int layer) {
        double mult = Math.min(2.0, 1.0 + 0.04 * Math.max(0, layer - 1));
        return (int) Math.ceil(base * mult / 5.0) * 5;
    }

    /**
     * 商店三卡槽（V12.8.3）：按当前节点稀有度权重（难度越高越容易出稀有/史诗）抽 3 张不重复卡。
     * 返回 JSONArray，每项 {rare, card:{id,name,rarity,effect}}。
     */
    public static JSONArray shopSlots(String kind, int layer, boolean endless, Random rnd) {
        double[] t = rarityTable(kind, layer, endless);
        List<Card> taken = new ArrayList<>();
        JSONArray arr = new JSONArray();
        for (int i = 0; i < 3; i++) {
            int r = rollSlotRarity(t, rnd.nextDouble(), 0);
            Card c = drawCardOfRarity(r, taken, null, rnd);
            if (c != null) taken.add(c);
            JSONObject s = new JSONObject();
            try {
                s.put("rare", r);
                if (c != null) s.put("card", c.toJson());
            } catch (Exception ignore) {}
            arr.put(s);
        }
        return arr;
    }

    /** 节点类型基础稀有度概率表 [common,rare,epic]。 */
    private static double[] rarityTable(String kind, int layer, boolean endless) {
        double[][] base = {
                {0.82, 0.16, 0.02},   // normal
                {0.55, 0.38, 0.07},   // elite
                {0.30, 0.55, 0.15},   // boss
                {0.12, 0.55, 0.33},   // champion
        };
        int k;
        if ("champion".equals(kind)) k = 3;
        else if ("boss".equals(kind)) k = 2;
        else if ("elite".equals(kind)) k = 1;
        else k = 0;
        double t0 = base[k][0], t1 = base[k][1], t2 = base[k][2];
        double d = Math.max(0, layer - 1);
        double rareAdd = endless ? d * 0.012 : d * 0.05;
        double epicAdd = endless ? d * 0.006 : d * 0.02;
        rareAdd = Math.min(rareAdd, 0.30);
        epicAdd = Math.min(epicAdd, 0.20);
        double move = Math.min(rareAdd + epicAdd, Math.max(0, t0 - 0.02));
        double scale = (rareAdd + epicAdd) > 0 ? move / (rareAdd + epicAdd) : 0;
        rareAdd *= scale; epicAdd *= scale;
        return new double[]{t0 - rareAdd - epicAdd, t1 + rareAdd, t2 + epicAdd};
    }

    private static int rollSlotRarity(double[] t, double r, int force) {
        if (force == 2) return 2;
        if (force == 1) return r < t[2] / (t[1] + t[2]) ? 2 : 1;
        if (r < t[2]) return 2;
        if (r < t[2] + t[1]) return 1;
        return 0;
    }

    /**
     * 三槽抽卡（V7.0 drawChoice）。
     * @param pity 当前普通保底计数（来自前端 run 状态）
     * @param epicPity 当前史诗保底计数
     * @return slots + 新的 pity/epicPity
     */
    public static JSONObject draw(String kind, int layer, boolean endless,
                                  int pity, int epicPity, List<String> ownedCardIds, Random rnd) {
        double[] t = rarityTable(kind, layer, endless);
        int force = 0;
        if (epicPity >= 6) force = 2;
        else if (pity >= 3) force = 1;

        List<Card> taken = new ArrayList<>();
        List<Card> slots = new ArrayList<>();
        int[] rars = new int[3];
        for (int i = 0; i < 3; i++) {
            int r = rollSlotRarity(t, rnd.nextDouble(), i == 0 ? force : 0);
            rars[i] = r;
            Card c = drawCardOfRarity(r, taken, ownedCardIds, rnd);
            if (c != null) taken.add(c);
            slots.add(c);
        }
        int maxR = Math.max(Math.max(rars[0], rars[1]), rars[2]);
        if (maxR == 0) { pity++; epicPity++; }
        else if (maxR == 1) { pity = 0; epicPity++; }
        else { pity = 0; epicPity = 0; }

        JSONArray arr = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONObject s = new JSONObject();
            try {
                s.put("rare", rars[i]);
                if (slots.get(i) != null) s.put("card", slots.get(i).toJson());
            } catch (Exception ignore) {}
            arr.put(s);
        }
        JSONObject out = new JSONObject();
        try {
            out.put("slots", arr);
            out.put("pity", pity);
            out.put("epicPity", epicPity);
        } catch (Exception ignore) {}
        return out;
    }

    /** 按稀有度抽一张：未持有且本槽未取过；不足时降档补池。 */
    private static Card drawCardOfRarity(int rare, List<Card> taken, List<String> owned, Random rnd) {
        List<Card> pool = new ArrayList<>();
        for (Card c : CARDS) {
            if (c.rare != rare) continue;
            if (owned != null && owned.contains(c.id)) continue;
            boolean used = false;
            for (Card x : taken) if (x.id.equals(c.id)) used = true;
            if (!used) pool.add(c);
        }
        if (pool.isEmpty()) {
            for (Card c : CARDS) {
                if (c.rare < rare) continue;
                if (owned != null && owned.contains(c.id)) continue;
                boolean used = false;
                for (Card x : taken) if (x.id.equals(c.id)) used = true;
                if (!used) pool.add(c);
            }
        }
        if (pool.isEmpty()) {
            for (Card c : CARDS) {
                if (owned != null && owned.contains(c.id)) continue;
                boolean used = false;
                for (Card x : taken) if (x.id.equals(c.id)) used = true;
                if (!used) pool.add(c);
            }
        }
        if (pool.isEmpty()) return null;
        return pool.get(rnd.nextInt(pool.size()));
    }
}
